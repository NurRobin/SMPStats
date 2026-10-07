package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.season.SeasonMeta;
import de.nurrobin.smpstats.season.SeasonService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/**
 * Ties the hall together: sends players in and out, builds their private scenes, watches the portals.
 * All Bukkit work happens on the main thread; database reads happen before the join, off-thread.
 */
public final class ThrowbackService {
    public static final String ACTION_RULES = "rules";
    private static final long TICK_PERIOD = 10L;
    private static final long PORTAL_COOLDOWN_MS = 5_000L;
    private static final long HINT_COOLDOWN_MS = 3_000L;
    private static final double RULES_REACH = 8.0;

    private record Origin(Location location) {
    }

    private final Plugin plugin;
    private final Logger logger;
    private final SeasonService seasons;
    private final CachedProgressRepository repository;
    private final ThrowbackFlow flow;
    private final SceneRenderer renderer;
    private final HallWorlds hall;
    private final MapArtWall wall;
    private final BooleanSupplier offlineMode;
    private final TrophyCatalog catalog = TrophyCatalog.standard();
    private final NamespacedKey previousMode;
    private final Path dataFolder;

    private final Map<UUID, SceneRenderer.SceneSession> visuals = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> builders = new ConcurrentHashMap<>();
    private final Map<UUID, Origin> origins = new ConcurrentHashMap<>();
    private final Map<UUID, Long> portalCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> hintCooldown = new ConcurrentHashMap<>();
    private final Set<UUID> leaving = ConcurrentHashMap.newKeySet();

    private java.util.function.BiFunction<Player, Location, java.util.concurrent.CompletableFuture<Boolean>> teleporter =
            (player, location) -> player.teleportAsync(location);
    private volatile ThrowbackSettings settings;
    private World wallsWorld;
    private volatile ThrowbackTexts texts;
    private volatile TrophyBoard board;
    private volatile ThrowbackContent content;
    private BukkitTask hallTask;
    private BukkitTask entranceTask;
    private BukkitTask unloadTask;

    public ThrowbackService(Plugin plugin, SeasonService seasons, CachedProgressRepository repository,
                            ThrowbackSettings settings, SceneRenderer renderer, HallWorlds hall, MapArtWall wall,
                            BooleanSupplier offlineMode, java.util.function.LongSupplier clock) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.seasons = seasons;
        this.repository = repository;
        this.settings = settings;
        this.renderer = renderer;
        this.hall = hall;
        this.wall = wall;
        this.offlineMode = offlineMode;
        this.dataFolder = plugin.getDataFolder().toPath();
        this.previousMode = new NamespacedKey(plugin, "throwback_prev_gm");
        this.flow = new ThrowbackFlow(repository, settings.rulesVersion(), clock);
        this.texts = ThrowbackTexts.load(dataFolder.resolve(settings.textsFile()), logger);
        hall.configure(settings.world());
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        if (!settings.enabled()) {
            logger.info("Throwback-Halle ist in der config.yml abgeschaltet");
            return;
        }
        if (!settings.layout().usable()) {
            logger.info("Throwback-Halle: Layout fehlt noch (Start, Regeltafel, Portal). Mit /throwback admin build entsteht ein Platzhalter-Gang.");
        }
        startEntranceTask();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::ensureBoard);
    }

    public void shutdown() {
        cancel(hallTask);
        cancel(entranceTask);
        cancel(unloadTask);
        hallTask = null;
        entranceTask = null;
        unloadTask = null;
        for (UUID uuid : new ArrayList<>(visuals.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                leaveHallNow(p);
            } else {
                closeVisuals(uuid);
            }
        }
        builders.values().forEach(BukkitTask::cancel);
        builders.clear();
        repository.close();
    }

    /** Test seam: MockBukkit has no teleportAsync. */
    void teleporter(java.util.function.BiFunction<Player, Location, java.util.concurrent.CompletableFuture<Boolean>> teleporter) {
        this.teleporter = teleporter;
    }

    private java.util.concurrent.CompletableFuture<Boolean> teleport(Player player, Location location) {
        return teleporter.apply(player, location);
    }

    private static void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    public synchronized void reload(ThrowbackSettings next) {
        ThrowbackSettings old = this.settings;
        this.settings = next;
        hall.configure(next.world());
        flow.setRulesVersion(next.rulesVersion());
        this.texts = ThrowbackTexts.load(dataFolder.resolve(next.textsFile()), logger);
        this.content = null;
        cancel(entranceTask);
        entranceTask = null;
        if (next.enabled()) {
            startEntranceTask();
        }
        if (!old.world().equals(next.world())) {
            logger.info("Throwback: neuer Weltname " + next.world() + " gilt ab dem nächsten Laden der Halle");
        }
    }

    public ThrowbackSettings settings() {
        return settings;
    }

    public ThrowbackFlow flow() {
        return flow;
    }

    public ThrowbackTexts texts() {
        return texts;
    }

    public CachedProgressRepository repository() {
        return repository;
    }

    // ------------------------------------------------------------------ data (async side)

    /** Runs on the async pre-login thread: reads the player's progress and makes sure the trophy data is loaded. */
    public void preLogin(UUID uuid) {
        if (!settings.enabled()) {
            return;
        }
        repository.preload(uuid);
        ensureBoard();
    }

    /** Loads the trophy board if the season has been imported. Blocking, never call on the main thread. */
    public synchronized void ensureBoard() {
        if (board != null) {
            return;
        }
        try {
            Optional<SeasonMeta> meta = seasons.meta(settings.season());
            if (meta.isEmpty()) {
                return;
            }
            board = TrophyBoard.load(seasons.store(), settings.season(), catalog);
            content = null;
            logger.info("Throwback: Trophäen-Daten der Saison " + settings.season() + " geladen (" + board.playerCount() + " Spieler)");
        } catch (SQLException e) {
            logger.warning("Throwback: Saison-Daten nicht lesbar: " + e.getMessage());
        }
    }

    /** Called after a season import finished, so the next join sees the new numbers. */
    public synchronized void invalidateBoard() {
        board = null;
        content = null;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::ensureBoard);
    }

    private ThrowbackContent content() {
        ThrowbackContent c = content;
        if (c == null) {
            TrophyBoard b = board;
            if (b == null) {
                b = new TrophyBoard(catalog, Map.of());
            }
            c = new ThrowbackContent(b, texts, settings, settings.matchByNameOffline() && offlineMode.getAsBoolean());
            content = c;
        }
        return c;
    }

    /** What the player named {@code name} (as listed in the season) would see; unknown names get the newcomer tour. */
    public HallPlan previewPlan(String name) {
        TrophyBoard b = board;
        UUID uuid = b == null ? UUID.randomUUID() : b.playerByName(name).map(p -> p.uuid()).orElse(UUID.randomUUID());
        return content().plan(uuid, name, settings.layout().stages().isEmpty() ? HallLayout.MAX_STAGES : settings.layout().stages().size());
    }

    // ------------------------------------------------------------------ joining and leaving

    private boolean active() {
        return settings.enabled() && settings.layout().usable();
    }

    /** Main thread, on PlayerJoinEvent. */
    public void onJoin(Player p) {
        restoreStaleMode(p);
        if (!settings.enabled()) {
            return;
        }
        boolean inHallWorld = hall.isHall(p.getWorld());
        if (!active()) {
            if (inHallWorld) {
                sendToExit(p);
            }
            return;
        }
        if (flow.onJoin(p.getUniqueId())) {
            enterHall(p, null);
        } else if (inHallWorld) {
            sendToExit(p);
        }
    }

    /** Main thread, on PlayerQuitEvent. */
    public void onQuit(Player p) {
        UUID uuid = p.getUniqueId();
        closeVisuals(uuid);
        flow.end(uuid);
        origins.remove(uuid);
        portalCooldown.remove(uuid);
        hintCooldown.remove(uuid);
        leaving.remove(uuid);
        restoreMode(p);
        scheduleUnload();
    }

    /** Main thread, on PlayerChangedWorldEvent: the player left the hall some other way than the portal. */
    public void onWorldChanged(Player p, World from) {
        if (hall.isHall(from) && !hall.isHall(p.getWorld())) {
            cleanupAfterLeaving(p);
        }
    }

    /** True if the teleport must be stopped: a mandatory tour player trying to leave without the portal. */
    public boolean blocksLeaving(Player p, Location from, Location to) {
        if (from == null || to == null || !hall.isHall(from.getWorld()) || hall.isHall(to.getWorld())) {
            return false;
        }
        UUID uuid = p.getUniqueId();
        if (leaving.contains(uuid)) {
            return false;
        }
        return flow.mode(uuid).filter(m -> m == ThrowbackFlow.Mode.TOUR).isPresent()
                && !p.hasPermission("smpstats.throwback.bypass");
    }

    /** {@code /throwback} and the entrance portal. */
    public boolean visit(Player p) {
        if (!active()) {
            p.sendMessage(texts.message("hall_closed"));
            return false;
        }
        UUID uuid = p.getUniqueId();
        if (flow.inHall(uuid) || hall.isHall(p.getWorld())) {
            return false;
        }
        Location origin = p.getLocation().clone();
        ThrowbackSettings.SpawnPortal portal = settings.spawnPortal();
        if (portal != null && p.getWorld().getName().equals(portal.world()) && portal.region().contains(origin)) {
            origin = entranceExit(p.getWorld());
        }
        if (!flow.completed(uuid)) {
            flow.onJoin(uuid);
        } else {
            flow.startVisit(uuid);
        }
        enterHall(p, origin);
        return true;
    }

    /** Where visitors land when the entrance region is where they stood. */
    private Location entranceExit(World world) {
        ThrowbackSettings.SpawnPortal portal = settings.spawnPortal();
        if (portal.exit() != null) {
            return portal.exit().at(world);
        }
        Location spawn = world.getSpawnLocation();
        return spawn.clone();
    }

    /** Loads the hall world and, when it was not loaded before, puts the picture walls up again (renderers are not saved). */
    private World loadHall() {
        World world = hall.load();
        if (world != null && world != wallsWorld) {
            wallsWorld = world;
            try {
                wall.build(world, settings.layout().walls(), dataFolder.resolve(settings.imagesFolder()));
            } catch (RuntimeException e) {
                logger.log(java.util.logging.Level.WARNING, "Throwback: Bilderwände konnten nicht aufgebaut werden", e);
            }
        }
        return world;
    }

    private void enterHall(Player p, Location origin) {
        UUID uuid = p.getUniqueId();
        World world = loadHall();
        if (world == null) {
            flow.end(uuid);
            p.sendMessage(texts.message("hall_closed"));
            return;
        }
        cancel(unloadTask);
        unloadTask = null;
        if (origin != null) {
            origins.put(uuid, new Origin(origin));
        }
        portalCooldown.put(uuid, System.currentTimeMillis());
        rememberMode(p);
        p.setGameMode(GameMode.ADVENTURE);
        closeVisuals(uuid);
        Location start = settings.layout().start().at(world);
        p.sendMessage(texts.message("prepare"));
        teleport(p, start).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (Boolean.TRUE.equals(ok) && p.isOnline() && flow.inHall(uuid) && hall.isHall(p.getWorld())) {
                buildScenes(p);
                startHallTask();
            } else if (p.isOnline() && flow.inHall(uuid)) {
                // teleport refused (another plugin): do not keep the player half in the tour
                flow.end(uuid);
                restoreMode(p);
            }
        }));
    }

    private void sendToExit(Player p) {
        Location exit = exitLocation();
        leaving.add(p.getUniqueId());
        teleport(p, exit).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            leaving.remove(p.getUniqueId());
            cleanupAfterLeaving(p);
        }));
    }

    private Location exitLocation() {
        ThrowbackSettings s = settings;
        if (s.exit() != null) {
            World w = Bukkit.getWorld(s.exitWorld().isEmpty() ? Bukkit.getWorlds().get(0).getName() : s.exitWorld());
            if (w != null) {
                return s.exit().at(w);
            }
        }
        for (World w : Bukkit.getWorlds()) {
            if (!hall.isHall(w)) {
                return w.getSpawnLocation();
            }
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private void leaveHallNow(Player p) {
        UUID uuid = p.getUniqueId();
        closeVisuals(uuid);
        flow.end(uuid);
        restoreMode(p);
    }

    private void cleanupAfterLeaving(Player p) {
        UUID uuid = p.getUniqueId();
        closeVisuals(uuid);
        flow.end(uuid);
        origins.remove(uuid);
        restoreMode(p);
        scheduleUnload();
    }

    private void closeVisuals(UUID uuid) {
        BukkitTask building = builders.remove(uuid);
        if (building != null) {
            building.cancel();
        }
        SceneRenderer.SceneSession session = visuals.remove(uuid);
        if (session != null) {
            session.close();
        }
    }

    // ------------------------------------------------------------------ game mode

    private void rememberMode(Player p) {
        var pdc = p.getPersistentDataContainer();
        if (!pdc.has(previousMode, PersistentDataType.STRING) && p.getGameMode() != GameMode.ADVENTURE) {
            pdc.set(previousMode, PersistentDataType.STRING, p.getGameMode().name());
        } else if (!pdc.has(previousMode, PersistentDataType.STRING)) {
            pdc.set(previousMode, PersistentDataType.STRING, GameMode.SURVIVAL.name());
        }
    }

    private void restoreMode(Player p) {
        var pdc = p.getPersistentDataContainer();
        String stored = pdc.get(previousMode, PersistentDataType.STRING);
        if (stored == null) {
            return;
        }
        pdc.remove(previousMode);
        try {
            p.setGameMode(GameMode.valueOf(stored));
        } catch (IllegalArgumentException e) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    /** A crash or kill while in the hall leaves the stored mode behind; give it back when the player is elsewhere. */
    private void restoreStaleMode(Player p) {
        if (!hall.isHall(p.getWorld()) && !flow.inHall(p.getUniqueId())) {
            restoreMode(p);
        }
    }

    // ------------------------------------------------------------------ scenes

    /** Builds the private scenes over a few ticks, one stage per tick, so a join does not spike the server. */
    void buildScenes(Player p) {
        UUID uuid = p.getUniqueId();
        World world = p.getWorld();
        HallLayout layout = settings.layout();
        HallPlan plan = content().plan(uuid, p.getName(), layout.stages().size());
        SceneRenderer.SceneSession session = renderer.open(p);
        visuals.put(uuid, session);
        List<Runnable> steps = new ArrayList<>();
        steps.add(() -> buildStart(session, world, layout, plan));
        steps.add(() -> buildRules(session, world, layout, plan));
        for (StagePlan stage : plan.stages()) {
            if (stage.index() >= layout.stages().size()) {
                continue;
            }
            steps.add(() -> buildStage(session, world, layout.stages().get(stage.index()), stage));
        }
        int[] next = {0};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (visuals.get(uuid) != session || !p.isOnline() || !hall.isHall(p.getWorld())) {
                self[0].cancel();
                builders.remove(uuid, self[0]);
                return;
            }
            steps.get(next[0]++).run();
            if (next[0] >= steps.size()) {
                self[0].cancel();
                builders.remove(uuid, self[0]);
            }
        }, 1L, 1L);
        builders.put(uuid, self[0]);
    }

    private void buildStart(SceneRenderer.SceneSession session, World world, HallLayout layout, HallPlan plan) {
        Pos start = layout.start();
        double rad = Math.toRadians(start.yaw());
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double rx = -Math.cos(rad);
        double rz = -Math.sin(rad);
        Location sign = new Location(world, start.x() + fx * 4, start.y() + 2.6, start.z() + fz * 4);
        session.text(sign, ThrowbackTexts.joinLines(plan.welcome()));
        if (plan.statueLabel() != null) {
            Location base = new Location(world, start.x() + fx * 3 + rx * 3, start.y(), start.z() + fz * 3 + rz * 3, start.yaw(), 0);
            SceneSpec statue = new SceneSpec(org.bukkit.entity.Pose.STANDING, null, null, null, null, 0, null, 180, List.of());
            session.figure(base, statue);
            session.text(base.clone().add(0, 2.4, 0), plan.statueLabel());
        }
    }

    private void buildRules(SceneRenderer.SceneSession session, World world, HallLayout layout, HallPlan plan) {
        Pos rules = layout.rules();
        List<Component> lines = new ArrayList<>(plan.rules());
        lines.add(Component.empty());
        lines.addAll(plan.privacy());
        Location board = new Location(world, rules.x(), rules.y() + 2.7, rules.z());
        session.text(board, ThrowbackTexts.joinLines(lines));
        Location button = new Location(world, rules.x(), rules.y() + 0.8, rules.z());
        session.text(button, content().rulesButton());
        session.interaction(new Location(world, rules.x(), rules.y(), rules.z()), 1.4f, 1.4f, ACTION_RULES);
    }

    private void buildStage(SceneRenderer.SceneSession session, World world, Pos pos, StagePlan stage) {
        Location base = pos.at(world);
        session.figure(base, SceneLibrary.spec(stage.scene()));
        long lines = lineCount(stage.text());
        session.text(base.clone().add(0, 2.35 + 0.1 * lines, 0), stage.text());
    }

    private static long lineCount(Component text) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text);
        return plain.lines().count();
    }

    /** {@code /throwback leave}: a visitor goes back; a player in the mandatory tour must use the portal. */
    public boolean leaveVisit(Player p) {
        UUID uuid = p.getUniqueId();
        if (!flow.inHall(uuid) || !hall.isHall(p.getWorld())) {
            p.sendMessage(texts.message("not_in_hall"));
            return false;
        }
        if (flow.mode(uuid).orElse(ThrowbackFlow.Mode.TOUR) == ThrowbackFlow.Mode.TOUR) {
            p.sendMessage(texts.message("portal_blocked"));
            return false;
        }
        handlePortal(p, p.getWorld(), settings.layout());
        return true;
    }

    // ------------------------------------------------------------------ rules

    /**
     * Confirms the rules. {@code requireNear} is set for the chat fallback, which must not work from across
     * the hall; the click on the station entity is already local.
     */
    public boolean confirmRules(Player p, boolean requireNear) {
        UUID uuid = p.getUniqueId();
        if (!flow.inHall(uuid) || !hall.isHall(p.getWorld())) {
            p.sendMessage(texts.message("not_in_hall"));
            return false;
        }
        if (requireNear && settings.layout().rules() != null) {
            Pos rules = settings.layout().rules();
            Location here = p.getLocation();
            double dx = here.getX() - rules.x();
            double dz = here.getZ() - rules.z();
            if (dx * dx + dz * dz > RULES_REACH * RULES_REACH) {
                p.sendMessage(texts.message("too_far"));
                return false;
            }
        }
        boolean wasRead = flow.rulesRead(uuid);
        flow.confirmRules(uuid);
        p.sendMessage(texts.message(wasRead ? "rules_already" : "rules_confirmed"));
        return true;
    }

    // ------------------------------------------------------------------ ticking

    private void startHallTask() {
        if (hallTask != null) {
            return;
        }
        hallTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickHall, TICK_PERIOD, TICK_PERIOD);
    }

    private void startEntranceTask() {
        ThrowbackSettings.SpawnPortal portal = settings.spawnPortal();
        if (entranceTask != null || portal == null) {
            return;
        }
        entranceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickEntrance, 40L, TICK_PERIOD);
    }

    /** Portal and void check, only runs while someone is in the hall. */
    void tickHall() {
        World world = hall.loaded();
        HallLayout layout = settings.layout();
        if (world == null || !layout.usable()) {
            stopHallTask();
            return;
        }
        List<Player> inHall = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (flow.inHall(p.getUniqueId())) {
                inHall.add(p);
            }
        }
        if (inHall.isEmpty()) {
            stopHallTask();
            return;
        }
        for (Player p : inHall) {
            Location at = p.getLocation();
            if (at.getY() < layout.start().y() - 30) {
                teleport(p, layout.start().at(world));
                continue;
            }
            if (layout.portal().contains(at)) {
                handlePortal(p, world, layout);
            }
        }
    }

    private void stopHallTask() {
        cancel(hallTask);
        hallTask = null;
    }

    private void handlePortal(Player p, World world, HallLayout layout) {
        UUID uuid = p.getUniqueId();
        ThrowbackFlow.Mode mode = flow.mode(uuid).orElse(ThrowbackFlow.Mode.VISIT);
        ThrowbackFlow.PortalDecision decision = flow.portal(uuid);
        switch (decision) {
            case BLOCKED -> {
                pushBack(p, world, layout);
                long now = System.currentTimeMillis();
                Long last = hintCooldown.get(uuid);
                if (last == null || now - last > HINT_COOLDOWN_MS) {
                    hintCooldown.put(uuid, now);
                    p.sendMessage(texts.message("portal_blocked"));
                    p.sendMessage(texts.message("rules_hint"));
                }
            }
            case FINISHED_TOUR, LEFT_VISIT -> {
                Location target = mode == ThrowbackFlow.Mode.TOUR || !origins.containsKey(uuid)
                        ? exitLocation() : origins.get(uuid).location();
                closeVisuals(uuid);
                leaving.add(uuid);
                portalCooldown.put(uuid, System.currentTimeMillis());
                teleport(p, target).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    leaving.remove(uuid);
                    origins.remove(uuid);
                    restoreMode(p);
                    p.sendMessage(texts.message(decision == ThrowbackFlow.PortalDecision.FINISHED_TOUR ? "tour_done" : "visit_left"));
                    scheduleUnload();
                }));
            }
            case NOT_IN_HALL -> { }
        }
    }

    private void pushBack(Player p, World world, HallLayout layout) {
        Region portal = layout.portal();
        Location here = p.getLocation();
        double dx = layout.start().x() - portal.centerX();
        double dz = layout.start().z() - portal.centerZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return;
        }
        Location back = here.clone().add(dx / len * 2.5, 0, dz / len * 2.5);
        teleport(p, back);
    }

    /** Entrance portal in the main world; cheap box test for the players of that world only. */
    void tickEntrance() {
        ThrowbackSettings.SpawnPortal portal = settings.spawnPortal();
        if (portal == null || !active()) {
            return;
        }
        World world = Bukkit.getWorld(portal.world());
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player p : world.getPlayers()) {
            if (portal.region().contains(p.getLocation())) {
                Long last = portalCooldown.get(p.getUniqueId());
                if (last == null || now - last > PORTAL_COOLDOWN_MS) {
                    portalCooldown.put(p.getUniqueId(), now);
                    visit(p);
                }
            }
        }
    }

    // ------------------------------------------------------------------ unloading

    private void scheduleUnload() {
        cancel(unloadTask);
        unloadTask = null;
        World world = hall.loaded();
        if (world == null || !world.getPlayers().isEmpty()) {
            return;
        }
        unloadTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            unloadTask = null;
            if (hall.unload()) {
                wallsWorld = null;
            }
        }, settings.unloadDelaySeconds() * 20L);
    }

    // ------------------------------------------------------------------ admin helpers

    /** Rebuilds the shared picture walls if the hall world is loaded. */
    public int rebuildWalls() {
        World world = hall.loaded();
        if (world == null) {
            return 0;
        }
        return wall.build(world, settings.layout().walls(), dataFolder.resolve(settings.imagesFolder()));
    }

    /** Loads the hall world (for building and for the walls) and (re)builds the picture walls. */
    public World loadForAdmin() {
        World world = loadHall();
        if (world != null) {
            cancel(unloadTask);
            unloadTask = null;
        }
        return world;
    }

    /** Applies a layout change and writes it to config.yml. */
    public void updateLayout(UnaryOperator<HallLayout> change) {
        HallLayout next = change.apply(settings.layout());
        plugin.getConfig().set("throwback.layout", null);
        next.write(plugin.getConfig().createSection("throwback.layout"));
        plugin.saveConfig();
        ThrowbackSettings s = settings;
        this.settings = new ThrowbackSettings(s.enabled(), s.world(), s.season(), s.minPlaytimeMinutes(), s.rankBadgeTop(),
                s.rulesVersion(), s.privacyUrl(), s.matchByNameOffline(), s.unloadDelaySeconds(), s.imagesFolder(),
                s.textsFile(), s.exitWorld(), s.exit(), s.spawnPortal(), next);
        this.content = null;
    }

    /** Test hook: swap the layout without touching config.yml. */
    void updateLayoutForTest(HallLayout layout) {
        ThrowbackSettings s = settings;
        this.settings = new ThrowbackSettings(s.enabled(), s.world(), s.season(), s.minPlaytimeMinutes(), s.rankBadgeTop(),
                s.rulesVersion(), s.privacyUrl(), s.matchByNameOffline(), s.unloadDelaySeconds(), s.imagesFolder(),
                s.textsFile(), s.exitWorld(), s.exit(), s.spawnPortal(), layout);
    }

    /** Staff override: marks the tour as done; a player who is in the hall right now is sent out. */
    public void skipTour(UUID uuid) {
        boolean inHall = flow.inHall(uuid);
        flow.markCompleted(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && inHall) {
            closeVisuals(uuid);
            sendToExit(p);
        }
    }

    /** Forgets a player's progress so they go through the mandatory tour again (for testing and corrections). */
    public void resetProgress(UUID uuid) {
        repository.forget(uuid);
    }

    public boolean inHall(UUID uuid) {
        return flow.inHall(uuid);
    }

    public boolean isHallWorld(World world) {
        return hall.isHall(world);
    }

    public int entityCount(UUID uuid) {
        SceneRenderer.SceneSession s = visuals.get(uuid);
        return s == null ? 0 : s.entityCount();
    }

    public boolean hasVisuals(UUID uuid) {
        return visuals.containsKey(uuid);
    }

    public Optional<Audience> audienceOf(Player p) {
        ThrowbackContent c = content();
        return Optional.of(c.audience(c.find(p.getUniqueId(), p.getName())));
    }
}
