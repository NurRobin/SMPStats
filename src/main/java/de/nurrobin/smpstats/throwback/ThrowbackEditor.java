package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The edit mode of the hall: a builder gets a hotbar of tools in place of their inventory, marks where start,
 * stages, boards, portal and picture walls go, sees each marker as a particle box that only they receive, and
 * saves the result as the hall layout. The real inventory lies in a file the whole time and comes back on every
 * way out (save, cancel, quit, kick, death, plugin disable, and at the next join after a crash). The unsaved draft
 * is kept in a file as well, so a restart only costs the session, not the work.
 * Everything here runs on the main thread.
 */
public final class ThrowbackEditor {
    static final double PARTICLE_RANGE = 32.0;
    static final double PARTICLE_SPACING = 0.5;
    static final int MAX_POINTS_PER_RUN = 1500;
    static final long PARTICLE_PERIOD = 10L;
    static final double REMOVE_RADIUS = 2.0;
    static final int DEFAULT_WALL_WIDTH = 4;
    static final int DEFAULT_WALL_HEIGHT = 3;
    static final int MAX_WALL_SIDE = 16;
    static final Color FREE = Color.LIME;
    static final Color BLOCKED = Color.RED;
    static final Color FLOOR = Color.AQUA;

    /** How a session ends: a saved or cancelled one drops its draft, any other keeps it for next time. */
    enum End { SAVED, CANCELLED, KEEP_DRAFT }

    private static final class Session {
        final UUID uuid;
        final World world;
        final EditDraft draft;
        List<EditElement> elements = List.of();
        SceneRenderer.SceneSession preview;
        int wallWidth = DEFAULT_WALL_WIDTH;
        int wallHeight = DEFAULT_WALL_HEIGHT;
        String wallTitle;

        Session(UUID uuid, World world, EditDraft draft) {
            this.uuid = uuid;
            this.world = world;
            this.draft = draft;
        }
    }

    private final Plugin plugin;
    private final Logger logger;
    private final ThrowbackService service;
    private final SceneRenderer renderer;
    private final EditEffects effects;
    private final Function<World, BlockProbe> probes;
    private final EditStore store;
    private final org.bukkit.NamespacedKey toolKey;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ThrowbackEditor(Plugin plugin, ThrowbackService service, SceneRenderer renderer) {
        this(plugin, service, renderer, EditEffects.PAPER, BlockProbe::of);
    }

    ThrowbackEditor(Plugin plugin, ThrowbackService service, SceneRenderer renderer, EditEffects effects,
                    Function<World, BlockProbe> probes) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.service = service;
        this.renderer = renderer;
        this.effects = effects;
        this.probes = probes;
        this.store = new EditStore(plugin.getDataFolder().toPath().resolve("throwback").resolve("edit"), logger);
        this.toolKey = EditTool.key(plugin);
    }

    // ------------------------------------------------------------------ queries

    public boolean inSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    EditTool toolOf(ItemStack item) {
        return EditTool.of(item, toolKey);
    }

    boolean isTool(ItemStack item) {
        return toolOf(item) != null;
    }

    EditDraft draft(UUID uuid) {
        Session s = sessions.get(uuid);
        return s == null ? null : s.draft;
    }

    List<EditElement> elements(UUID uuid) {
        Session s = sessions.get(uuid);
        return s == null ? List.of() : s.elements;
    }

    EditStore store() {
        return store;
    }

    /** What the actionbar shows during a session. */
    EditReport report(UUID uuid) {
        Session s = sessions.get(uuid);
        return s == null ? null : EditReport.of(s.draft, service.settings().editMinStages(), s.elements);
    }

    // ------------------------------------------------------------------ lifecycle

    /** {@code /throwback admin edit}: teleports into the hall if needed, then starts the session. */
    public void begin(Player p) {
        UUID uuid = p.getUniqueId();
        ThrowbackTexts texts = service.texts();
        Session running = sessions.get(uuid);
        if (running != null) {
            // blocks changed by other means (WorldEdit, commands) are not noticed, so running the command again checks them
            refreshElements(running);
            p.sendMessage(texts.message("edit_already"));
            return;
        }
        if (service.inHall(uuid)) {
            p.sendMessage(texts.message("edit_in_tour"));
            return;
        }
        World hallWorld = service.loadForAdmin();
        if (hallWorld == null) {
            p.sendMessage(texts.message("hall_closed"));
            return;
        }
        if (service.isHallWorld(p.getWorld())) {
            open(p);
            return;
        }
        Pos start = service.settings().layout().start();
        Location target = start == null ? new Location(hallWorld, 0.5, 64, 0.5) : start.at(hallWorld);
        service.teleport(p, target).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (Boolean.TRUE.equals(ok) && p.isOnline() && service.isHallWorld(p.getWorld())) {
                open(p);
            } else if (p.isOnline()) {
                p.sendMessage(texts.message("hall_closed"));
            }
        }));
    }

    private void open(Player p) {
        UUID uuid = p.getUniqueId();
        if (sessions.containsKey(uuid)) {
            return;
        }
        ThrowbackTexts texts = service.texts();
        if (store.hasInventory(uuid)) {
            // never overwrite a backup: whatever is in the inventory now is not the real one
            restoreInventory(p);
        }
        Optional<EditDraft> resumed = store.loadDraft(uuid);
        EditDraft draft = resumed.orElseGet(() -> EditDraft.fromLayout(service.settings().layout()));
        PlayerInventory inv = p.getInventory();
        try {
            store.saveInventory(uuid, new EditStore.SavedInventory(slots(inv.getStorageContents()), slots(inv.getArmorContents()),
                    solid(inv.getItemInOffHand())));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Throwback: Inventar von " + p.getName() + " ließ sich nicht sichern", e);
            p.sendMessage(texts.message("edit_backup_failed"));
            return;
        }
        inv.clear();
        for (EditTool tool : EditTool.values()) {
            inv.setItem(tool.ordinal(), toolItem(tool));
        }
        inv.setHeldItemSlot(0);
        Session s = new Session(uuid, p.getWorld(), draft);
        sessions.put(uuid, s);
        refresh(p, s);
        ensureTask();
        p.sendMessage(texts.message(resumed.isPresent() ? "edit_resumed" : "edit_started"));
    }

    private static List<ItemStack> slots(ItemStack[] raw) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack item : raw) {
            out.add(solid(item));
        }
        return out;
    }

    private static ItemStack solid(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    /** {@code /throwback admin edit save}, and the save tool. */
    public void save(Player p) {
        Session s = sessions.get(p.getUniqueId());
        ThrowbackTexts texts = service.texts();
        if (s == null) {
            p.sendMessage(texts.message("edit_not_active"));
            return;
        }
        EditReport report = EditReport.of(s.draft, service.settings().editMinStages(), s.elements);
        if (!report.complete()) {
            p.sendMessage(texts.message("edit_save_missing"));
            for (Component line : checkLines(report)) {
                p.sendMessage(line);
            }
            return;
        }
        HallLayout layout = s.draft.toLayout();
        try {
            service.applyEditedLayout(layout);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Throwback: Layout aus dem Bearbeitungsmodus ließ sich nicht speichern", e);
            p.sendMessage(texts.message("edit_save_failed"));
            return;
        }
        end(p, End.SAVED, null);
        p.sendMessage(texts.message("edit_saved", Map.of("stages", Component.text(layout.stages().size()),
                "walls", Component.text(layout.walls().size()))));
        for (EditElement e : report.blocked()) {
            p.sendMessage(texts.message("edit_warn_blocked", Map.of("label", elementLabel(e))));
        }
    }

    /** {@code /throwback admin edit cancel}: the draft is dropped. */
    public void cancel(Player p) {
        if (!sessions.containsKey(p.getUniqueId())) {
            p.sendMessage(service.texts().message("edit_not_active"));
            return;
        }
        end(p, End.CANCELLED, "edit_cancelled");
    }

    /** Ends the session of a player who left the hall world on their own. */
    public void onWorldChanged(Player p) {
        if (sessions.containsKey(p.getUniqueId()) && !service.isHallWorld(p.getWorld())) {
            end(p, End.KEEP_DRAFT, "edit_left_world");
        }
    }

    /** Quit and kick: the inventory goes back into the player data before it is written. */
    public void onQuit(Player p) {
        end(p, End.KEEP_DRAFT, null);
    }

    /** Join: a backup left behind by a crash is restored, and tools that stayed in the inventory are removed. */
    public void onJoin(Player p) {
        UUID uuid = p.getUniqueId();
        if (sessions.containsKey(uuid)) {
            return;
        }
        if (store.hasInventory(uuid)) {
            if (restoreInventory(p)) {
                p.sendMessage(service.texts().message("edit_restored"));
            }
        } else {
            removeTools(p);
        }
    }

    /** Death: the tools do not drop, the inventory is kept and put back to what it was before the session. */
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!sessions.containsKey(p.getUniqueId())) {
            return;
        }
        event.getDrops().removeIf(this::isTool);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        end(p, End.KEEP_DRAFT, null);
    }

    public void shutdown() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                end(p, End.KEEP_DRAFT, null);
            } else {
                Session s = sessions.remove(uuid);
                if (s != null && s.preview != null) {
                    s.preview.close();
                }
            }
        }
        stopTask();
    }

    private void end(Player p, End how, String messageKey) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) {
            return;
        }
        if (s.preview != null) {
            s.preview.close();
            s.preview = null;
        }
        restoreInventory(p);
        if (how != End.KEEP_DRAFT) {
            store.deleteDraft(p.getUniqueId());
        }
        if (sessions.isEmpty()) {
            stopTask();
        }
        if (p.isOnline()) {
            p.sendActionBar(Component.empty());
            if (messageKey != null) {
                p.sendMessage(service.texts().message(messageKey));
            }
        }
    }

    // ------------------------------------------------------------------ inventory

    private ItemStack toolItem(EditTool tool) {
        ThrowbackTexts texts = service.texts();
        ItemStack item = new ItemStack(tool.material());
        item.editMeta(meta -> {
            meta.displayName(texts.message("edit_tool_" + tool.id()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(texts.message("edit_tool_" + tool.id() + "_lore")
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.id());
        });
        return item;
    }

    void removeTools(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTool(contents[i])) {
                inv.setItem(i, null);
            }
        }
        if (isTool(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
        }
    }

    /**
     * Puts the backed-up inventory back. Tools are removed first; anything else the player holds now (picked up
     * during the session) is added on top of the restored items instead of being thrown away.
     * Returns false if there was no usable backup.
     */
    boolean restoreInventory(Player p) {
        UUID uuid = p.getUniqueId();
        removeTools(p);
        Optional<EditStore.SavedInventory> saved = store.loadInventory(uuid);
        if (saved.isEmpty()) {
            return false;
        }
        PlayerInventory inv = p.getInventory();
        List<ItemStack> extras = new ArrayList<>();
        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) {
                extras.add(item.clone());
            }
        }
        EditStore.SavedInventory backup = saved.get();
        inv.clear();
        inv.setStorageContents(backup.storage().toArray(new ItemStack[0]));
        inv.setArmorContents(backup.armor().toArray(new ItemStack[0]));
        inv.setItemInOffHand(backup.offHand());
        for (ItemStack extra : extras) {
            inv.addItem(extra).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        }
        store.deleteInventory(uuid);
        return true;
    }

    // ------------------------------------------------------------------ clicking

    /** Right click on a block with a tool. */
    void use(Player p, EditTool tool, Block block, Location hit) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || !service.isHallWorld(p.getWorld())) {
            return;
        }
        ThrowbackTexts texts = service.texts();
        switch (tool) {
            case UNDO -> undo(p, s);
            case SAVE -> save(p);
            default -> {
                EditMark mark = markFor(p, s, tool.kind(), block, hit);
                EditDraft.Placed result = s.draft.place(mark);
                switch (result) {
                    case STAGE_LIMIT -> p.sendMessage(texts.message("edit_stage_limit",
                            Map.of("max", Component.text(HallLayout.MAX_STAGES))));
                    case WALL_NAME_TAKEN -> p.sendMessage(texts.message("edit_wall_taken"));
                    default -> {
                        changed(p, s, mark.kind());
                        p.sendMessage(texts.message("edit_placed", Map.of("label", markLabel(mark, s.draft))));
                    }
                }
            }
        }
    }

    private EditMark markFor(Player p, Session s, EditKind kind, Block block, Location hit) {
        float yaw = p.getLocation().getYaw();
        if (kind == EditKind.WALL) {
            int n = s.draft.nextWallNumber();
            String title = s.wallTitle != null ? s.wallTitle : plain(service.texts().message("edit_wall_title",
                    Map.of("n", Component.text(n))));
            return EditMark.ofWall(EditGeometry.wall(block.getX(), block.getY(), block.getZ(), yaw, "bauten-" + n,
                    s.wallWidth, s.wallHeight, title, ""));
        }
        return EditGeometry.mark(kind, block.getX(), block.getY(), block.getZ(), hit == null ? null : hit.getX(),
                hit == null ? null : hit.getZ(), yaw);
    }

    /** Left click on a block with a tool: removes the tool's marker nearest to that block. */
    void remove(Player p, EditTool tool, Block block) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || tool.kind() == null || !service.isHallWorld(p.getWorld())) {
            return;
        }
        ThrowbackTexts texts = service.texts();
        Optional<EditMark> found = s.draft.nearest(tool.kind(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5,
                REMOVE_RADIUS);
        if (found.isEmpty()) {
            p.sendMessage(texts.message("edit_nothing_near"));
            return;
        }
        Component label = markLabel(found.get(), s.draft);
        s.draft.remove(found.get());
        changed(p, s, tool.kind());
        p.sendMessage(texts.message("edit_removed", Map.of("label", label)));
    }

    private void undo(Player p, Session s) {
        ThrowbackTexts texts = service.texts();
        if (s.draft.marks().isEmpty()) {
            p.sendMessage(texts.message("edit_undo_empty"));
            return;
        }
        EditMark last = s.draft.marks().get(s.draft.marks().size() - 1);
        Component label = markLabel(last, s.draft);
        s.draft.undo();
        changed(p, s, last.kind());
        p.sendMessage(texts.message("edit_undone", Map.of("label", label)));
    }

    /** {@code /throwback admin edit wallsize <b> <h> [titel]}: size and title of the walls placed from now on. */
    public void wallSize(Player p, int width, int height, String title) {
        Session s = sessions.get(p.getUniqueId());
        ThrowbackTexts texts = service.texts();
        if (s == null) {
            p.sendMessage(texts.message("edit_not_active"));
            return;
        }
        s.wallWidth = Math.max(1, Math.min(MAX_WALL_SIDE, width));
        s.wallHeight = Math.max(1, Math.min(MAX_WALL_SIDE, height));
        s.wallTitle = title == null || title.isBlank() ? null : title;
        p.sendMessage(texts.message("edit_wall_size", Map.of("width", Component.text(s.wallWidth),
                "height", Component.text(s.wallHeight),
                "title", Component.text(s.wallTitle == null ? plain(texts.message("edit_wall_title", Map.of("n", Component.text("n"))))
                        : s.wallTitle))));
    }

    /** A block of the hall changed under the builder's hands; boxes near it are checked again next tick. */
    void blockChanged(Player p, Block block) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || block.getWorld() != s.world) {
            return;
        }
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sessions.get(p.getUniqueId()) != s) {
                return;
            }
            BlockProbe probe = probes.apply(s.world);
            for (EditElement e : s.elements) {
                if (e.near(x, y, z)) {
                    e.recompute(probe);
                }
            }
        });
    }

    // ------------------------------------------------------------------ draft changes

    /** Writes the draft, checks every box again and, for the boards, redraws the live preview. */
    private void changed(Player p, Session s, EditKind kind) {
        try {
            store.saveDraft(s.uuid, s.draft);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Throwback: Entwurf von " + p.getName() + " ließ sich nicht speichern", e);
        }
        refreshElements(s);
        if (kind == EditKind.RULES || kind == EditKind.PRIVACY) {
            refreshPreview(p, s);
        }
    }

    private void refresh(Player p, Session s) {
        refreshElements(s);
        refreshPreview(p, s);
    }

    /** Called when the viewer was teleported: entities shown to one player can need showing again (Paper #12599). */
    public void onTeleported(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sessions.get(p.getUniqueId()) == s && p.isOnline()) {
                    refreshPreview(p, s);
                }
            });
        }
    }

    private void refreshElements(Session s) {
        BlockProbe probe = probes.apply(s.world);
        List<EditElement> elements = EditElement.of(s.draft, panelSizes());
        elements.forEach(e -> e.recompute(probe));
        s.elements = elements;
    }

    private EditElement.PanelSizes panelSizes() {
        ThrowbackTexts texts = service.texts();
        ThrowbackSettings settings = service.settings();
        return new EditElement.PanelSizes(PanelMetrics.height(texts.rules(settings.rulesVersion())),
                PanelMetrics.height(texts.privacy(settings.privacyUrl())));
    }

    /** The real boards, exactly as the tour builds them, shown to the builder only and not saved with the world. */
    private void refreshPreview(Player p, Session s) {
        if (s.preview != null) {
            s.preview.close();
            s.preview = null;
        }
        Optional<EditMark> rules = s.draft.single(EditKind.RULES);
        Optional<EditMark> privacy = s.draft.single(EditKind.PRIVACY);
        if (rules.isEmpty() && privacy.isEmpty()) {
            return;
        }
        ThrowbackTexts texts = service.texts();
        ThrowbackSettings settings = service.settings();
        List<Component> privacyLines = texts.privacy(settings.privacyUrl());
        Component privacyText = ThrowbackTexts.joinLines(privacyLines);
        SceneRenderer.SceneSession preview = renderer.open(p);
        s.preview = preview;
        if (rules.isPresent()) {
            Pos station = rules.get().pos();
            preview.panel(PanelMetrics.rulesBoard(s.world, station),
                    ThrowbackTexts.joinLines(texts.rules(settings.rulesVersion())), PanelMetrics.SCALE);
            preview.text(new Location(s.world, station.x(), station.y() + 0.8, station.z()), texts.button());
            if (privacy.isEmpty() && !privacyLines.isEmpty()) {
                preview.panel(PanelMetrics.privacyBoard(s.world, station, null), privacyText, PanelMetrics.SCALE);
            }
        }
        if (!privacyLines.isEmpty()) {
            privacy.ifPresent(m -> preview.panel(PanelMetrics.privacyBoard(s.world, m.pos(), m.pos()), privacyText, PanelMetrics.SCALE));
        }
    }

    // ------------------------------------------------------------------ particles and checklist

    private void ensureTask() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PARTICLE_PERIOD, PARTICLE_PERIOD);
        }
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Draws the boxes near each builder and refreshes the checklist; reads the cached blocked flags only. */
    void tick() {
        for (Session s : sessions.values()) {
            Player p = Bukkit.getPlayer(s.uuid);
            if (p == null || !p.isOnline() || p.getWorld() != s.world) {
                continue;
            }
            draw(p, s);
            p.sendActionBar(checklist(EditReport.of(s.draft, service.settings().editMinStages(), s.elements)));
        }
    }

    /** Sends the edges of all boxes within range, nearest first, until the point budget of this run is used up. Returns the points sent. */
    private int draw(Player p, Session s) {
        Location at = p.getLocation();
        List<EditElement> near = new ArrayList<>();
        for (EditElement e : s.elements) {
            OrientedBox.Pt c = e.center();
            double dx = c.x() - at.getX();
            double dy = c.y() - at.getY();
            double dz = c.z() - at.getZ();
            if (dx * dx + dy * dy + dz * dz <= PARTICLE_RANGE * PARTICLE_RANGE) {
                near.add(e);
            }
        }
        near.sort(Comparator.comparingDouble(e -> {
            OrientedBox.Pt c = e.center();
            return Math.pow(c.x() - at.getX(), 2) + Math.pow(c.y() - at.getY(), 2) + Math.pow(c.z() - at.getZ(), 2);
        }));
        int budget = MAX_POINTS_PER_RUN;
        for (EditElement e : near) {
            Color color = e.blocked() ? BLOCKED : FREE;
            List<OrientedBox> boxes = new ArrayList<>(e.footprint());
            boxes.addAll(e.clearance());
            for (OrientedBox box : boxes) {
                List<OrientedBox.Pt> points = box.edgePoints(PARTICLE_SPACING);
                if (points.size() > budget) {
                    return MAX_POINTS_PER_RUN - budget;
                }
                Color c = e.footprint().contains(box) ? FLOOR : color;
                for (OrientedBox.Pt pt : points) {
                    effects.dust(p, new Location(s.world, pt.x(), pt.y(), pt.z()), c);
                }
                budget -= points.size();
            }
        }
        return MAX_POINTS_PER_RUN - budget;
    }

    /** Test hook: one drawing run for a builder; returns the number of points sent. */
    int drawForTest(Player p) {
        Session s = sessions.get(p.getUniqueId());
        return s == null ? 0 : draw(p, s);
    }

    private List<Component> checkLines(EditReport report) {
        ThrowbackTexts texts = service.texts();
        List<Component> lines = new ArrayList<>();
        for (EditReport.Check c : report.checks()) {
            Component label = texts.message("edit_label_" + c.id(), Map.of("have", Component.text(c.have()),
                    "need", Component.text(c.need())));
            lines.add(texts.message(c.ok() ? "edit_check_ok" : "edit_check_bad", Map.of("label", label)));
        }
        return lines;
    }

    private Component checklist(EditReport report) {
        Component out = Component.empty();
        List<Component> lines = checkLines(report);
        for (int i = 0; i < lines.size(); i++) {
            out = out.append(i == 0 ? Component.empty() : Component.text(" | ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                    .append(lines.get(i));
        }
        return out;
    }

    private Component elementLabel(EditElement e) {
        return service.texts().message("edit_element_" + e.type(), Map.of("detail", Component.text(e.detail())));
    }

    private Component markLabel(EditMark m, EditDraft draft) {
        String type;
        String detail = "";
        switch (m.kind()) {
            case START -> type = "start";
            case STAGE -> {
                type = "stage";
                detail = String.valueOf(draft.stages().indexOf(m) + 1);
            }
            case RULES -> type = "rules";
            case PRIVACY -> type = "privacy";
            case PORTAL_1 -> {
                type = "portal_corner";
                detail = "1";
            }
            case PORTAL_2 -> {
                type = "portal_corner";
                detail = "2";
            }
            default -> {
                type = "wall";
                detail = m.wall().name();
            }
        }
        return service.texts().message("edit_element_" + type, Map.of("detail", Component.text(detail)));
    }

    private static String plain(Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }
}
