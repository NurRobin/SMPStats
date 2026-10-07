package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.season.SeasonPlayer;
import de.nurrobin.smpstats.season.SeasonRanking;
import de.nurrobin.smpstats.season.SeasonStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trophy values of every player of one season, computed once and kept in memory (a few thousand numbers for
 * 69 players). A join then only does lookups; no database access happens on the main thread.
 */
public final class TrophyBoard {
    private final TrophyCatalog catalog;
    private final Map<UUID, SeasonPlayer> players;
    private final Map<String, Map<UUID, Value>> values = new HashMap<>();
    private final Map<String, Map<UUID, Double>> numbers = new HashMap<>();

    public TrophyBoard(TrophyCatalog catalog, Map<UUID, PlayerData> data) {
        this.catalog = catalog;
        this.players = new HashMap<>();
        data.forEach((uuid, d) -> players.put(uuid, d.player()));
        for (TrophyDef def : catalog.defs()) {
            Map<UUID, Value> perPlayer = new HashMap<>();
            Map<UUID, Double> perNumber = new HashMap<>();
            for (Map.Entry<UUID, PlayerData> e : data.entrySet()) {
                Value v = def.value().apply(e.getValue());
                if (v != null && v.number() > 0) {
                    perPlayer.put(e.getKey(), v);
                    perNumber.put(e.getKey(), v.number());
                }
            }
            values.put(def.id(), perPlayer);
            numbers.put(def.id(), perNumber);
        }
    }

    public static TrophyBoard load(SeasonStore store, String season, TrophyCatalog catalog) throws SQLException {
        Map<UUID, SeasonPlayer> players = store.players(season);
        Map<UUID, Map<String, Double>> stats = store.statsMatching(season, TrophyCatalog.exactKeys(), TrophyCatalog.prefixes());
        Map<UUID, Map<String, Long>> adv = store.advancementsOf(season, TrophyCatalog.advancementKeys());
        Map<UUID, PlayerData> data = new HashMap<>();
        for (SeasonPlayer p : players.values()) {
            data.put(p.uuid(), new PlayerData(p, stats.getOrDefault(p.uuid(), Map.of()), adv.getOrDefault(p.uuid(), Map.of())));
        }
        return new TrophyBoard(catalog, data);
    }

    public int playerCount() {
        return players.size();
    }

    public Optional<SeasonPlayer> player(UUID uuid) {
        return Optional.ofNullable(players.get(uuid));
    }

    /** Case-insensitive lookup, used on offline-mode servers where uuids do not match the online-mode season. */
    public Optional<SeasonPlayer> playerByName(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return players.values().stream().filter(p -> p.name().toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }

    /** All trophies of the player with a value above zero, in catalog order. */
    public List<Trophy> trophies(UUID uuid) {
        List<Trophy> out = new ArrayList<>();
        List<TrophyDef> defs = catalog.defs();
        for (int i = 0; i < defs.size(); i++) {
            TrophyDef def = defs.get(i);
            Value v = values.get(def.id()).get(uuid);
            if (v == null) {
                continue;
            }
            Map<UUID, Double> all = numbers.get(def.id());
            Optional<SeasonRanking.Rank> rank;
            if (def.kind() == TrophyDef.Kind.EARLIEST) {
                Map<UUID, Long> times = new HashMap<>();
                all.forEach((k, n) -> times.put(k, n.longValue()));
                rank = SeasonRanking.earliest(times, uuid);
            } else {
                rank = SeasonRanking.highest(all, uuid);
            }
            out.add(new Trophy(def, v, rank, i));
        }
        return out;
    }
}
