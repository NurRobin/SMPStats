package de.nurrobin.smpstats.moments;

import com.google.gson.Gson;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MomentService {
    private final Plugin plugin;
    private final StatsStorage storage;
    private Settings settings;
    private List<MomentDefinition> definitions = new ArrayList<>();
    private final Map<Key, ActiveWindow> windows = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private int flushTaskId = -1;

    public MomentService(Plugin plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
        this.definitions = settings.getMomentDefinitions();
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
        this.definitions = settings.getMomentDefinitions();
    }

    public void start() {
        if (!settings.isMomentsEnabled()) {
            return;
        }
        long periodTicks = Math.max(1, settings.getMomentsFlushSeconds()) * 20L;
        flushTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushStale, periodTicks, periodTicks).getTaskId();
    }

    public void shutdown() {
        if (flushTaskId != -1) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        flushAll();
    }

    public void onBlockBreak(Player player, Location location, org.bukkit.Material material) {
        if (!settings.isMomentsEnabled()) return;
        for (MomentDefinition def : definitions) {
            if (def.getTrigger() != MomentDefinition.TriggerType.BLOCK_BREAK) continue;
            if (!def.matchesMaterial(material)) continue;
            handleWindow(player.getUniqueId(), def, location);
        }
    }

    public void onDeath(Player player, Location location, double fallDistance, String cause, boolean selfExplosion) {
        if (!settings.isMomentsEnabled()) return;
        for (MomentDefinition def : definitions) {
            switch (def.getTrigger()) {
                case FIRST_DEATH -> handleFirstOnly(player, def, location);
                case DEATH_FALL -> {
                    if (fallDistance >= def.getMinFallDistance() && def.matchesCause(cause)) {
                        emitInstant(player, def, location, Map.of("fall", fallDistance));
                    }
                }
                case DEATH_EXPLOSION -> {
                    if (def.matchesCause(cause) && (!def.isRequireSelf() || selfExplosion)) {
                        emitInstant(player, def, location, Map.of("cause", cause));
                    }
                }
                case DEATH -> {
                    if (def.matchesCause(cause)) {
                        emitInstant(player, def, location, Map.of("cause", cause));
                    }
                }
                default -> { }
            }
        }
    }

    public void onDamage(Player player, double finalDamage, String cause) {
        if (!settings.isMomentsEnabled()) return;
        double resultingHealth = Math.max(0, player.getHealth() - finalDamage);
        for (MomentDefinition def : definitions) {
            if (def.getTrigger() != MomentDefinition.TriggerType.DAMAGE_LOW_HP) continue;
            if (!def.matchesCause(cause)) continue;
            if (resultingHealth > 0 && resultingHealth <= def.getMaxHealthAfterDamage()) {
                emitInstant(player, def, player.getLocation(), Map.of("health", String.format("%.1f", resultingHealth)));
            }
        }
    }

    public List<MomentEntry> getRecentMoments(int limit) {
        try {
            return storage.loadRecentMoments(limit);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load recent moments: " + e.getMessage());
            return List.of();
        }
    }

    public List<MomentEntry> getMomentsSince(long sinceMillis, int limit) {
        try {
            return storage.loadMomentsSince(sinceMillis, limit);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load moments since: " + e.getMessage());
            return List.of();
        }
    }

    public List<MomentEntry> queryMoments(java.util.UUID playerId, String type, long sinceMillis, int limit) {
        try {
            return storage.queryMoments(playerId, type, sinceMillis, limit);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not query moments: " + e.getMessage());
            return List.of();
        }
    }

    private void flushStale() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Key, ActiveWindow> entry : windows.entrySet()) {
            ActiveWindow window = entry.getValue();
            long windowMillis = window.mergeSeconds * 1000L;
            if ((now - window.lastUpdate) > windowMillis) {
                flushWindow(entry.getKey(), window);
            }
        }
    }

    private void flushAll() {
        for (Map.Entry<Key, ActiveWindow> entry : windows.entrySet()) {
            flushWindow(entry.getKey(), entry.getValue());
        }
        windows.clear();
    }

    private void flushWindow(Key key, ActiveWindow window) {
        if (window == null) {
            return;
        }
        if (window.count > 0 && window.origin != null) {
            String detail = window.detailTemplate.replace("{count}", String.valueOf(window.count)).replace("{player}", window.playerName);
            String payload = gson.toJson(Map.of("count", window.count));
            saveMoment(MomentEntry.fromLocation(key.playerId, key.definitionId, window.title, detail, payload, window.origin, window.startedAt, window.lastUpdate));
        }
        windows.remove(key);
    }

    private void saveMoment(MomentEntry entry) {
        try {
            storage.saveMoment(entry);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save moment: " + e.getMessage());
        }
    }

    private void handleWindow(UUID playerId, MomentDefinition def, Location location) {
        long now = System.currentTimeMillis();
        Key key = new Key(playerId, def.getId());
        ActiveWindow window = windows.get(key);
        long windowMillis = def.getMergeSeconds() * 1000L;
        if (window == null || (now - window.lastUpdate) > windowMillis) {
            flushWindow(key, window);
            window = new ActiveWindow(def.getId(), def.getTitle(), def.getDetail(), def.getMergeSeconds(), location, now, resolvePlayerName(playerId));
            windows.put(key, window);
        }
        window.count++;
        window.lastUpdate = now;
        window.lastLocation = location;
    }

    private void handleFirstOnly(Player player, MomentDefinition def, Location location) {
        try {
            if (def.isFirstOnly() && storage.hasMoment(player.getUniqueId(), def.getId())) {
                return;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Moment duplicate check failed: " + e.getMessage());
        }
        emitInstant(player, def, location, Map.of());
    }

    private void emitInstant(Player player, MomentDefinition def, Location location, Map<String, Object> payload) {
        String detail = format(def.getDetail(), player, payload);
        String title = format(def.getTitle(), player, payload);
        String payloadJson = payload.isEmpty() ? null : gson.toJson(payload);
        saveMoment(MomentEntry.fromLocation(player.getUniqueId(), def.getId(), title, detail, payloadJson, location, System.currentTimeMillis(), System.currentTimeMillis()));
    }

    private String format(String template, Player player, Map<String, Object> payload) {
        if (template == null) {
            return "";
        }
        String out = template.replace("{player}", player.getName());
        if (payload.containsKey("fall")) {
            out = out.replace("{fall}", String.format("%.1f", payload.get("fall")));
        }
        if (payload.containsKey("count")) {
            out = out.replace("{count}", String.valueOf(payload.get("count")));
        }
        if (payload.containsKey("health")) {
            out = out.replace("{health}", String.valueOf(payload.get("health")));
        }
        return out;
    }

    private String resolvePlayerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : "unknown";
    }

    private record Key(UUID playerId, String definitionId) {
    }

    private static class ActiveWindow {
        private final String type;
        private final String title;
        private final String detailTemplate;
        private final long mergeSeconds;
        private final long startedAt;
        private long lastUpdate;
        private int count;
        private Location origin;
        private Location lastLocation;
        private final String playerName;

        ActiveWindow(String type, String title, String detailTemplate, long mergeSeconds, Location origin, long startedAt, String playerName) {
            this.type = type;
            this.title = title;
            this.detailTemplate = detailTemplate != null ? detailTemplate : "";
            this.mergeSeconds = mergeSeconds;
            this.origin = origin;
            this.startedAt = startedAt;
            this.lastUpdate = startedAt;
            this.playerName = playerName;
        }
    }
}
