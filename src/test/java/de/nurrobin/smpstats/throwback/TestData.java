package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.season.SeasonPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Invented players for the trophy tests; no real data ever. */
final class TestData {
    static final long HOUR = 3_600_000L;
    static final String CUSTOM = "minecraft:custom/minecraft:";

    private TestData() {
    }

    static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    static PlayerData player(int n, String name, long playtimeMs, Map<String, Double> stats) {
        return new PlayerData(new SeasonPlayer(id(n), name, playtimeMs), stats, Map.of());
    }

    static PlayerData player(int n, String name, long playtimeMs, Map<String, Double> stats, Map<String, Long> adv) {
        return new PlayerData(new SeasonPlayer(id(n), name, playtimeMs), stats, adv);
    }

    static Map<String, Double> stats(Object... kv) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        }
        return m;
    }

    static Map<UUID, PlayerData> table(PlayerData... players) {
        Map<UUID, PlayerData> out = new HashMap<>();
        for (PlayerData p : players) {
            out.put(p.player().uuid(), p);
        }
        return out;
    }
}
