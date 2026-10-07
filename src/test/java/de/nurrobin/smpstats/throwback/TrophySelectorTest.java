package de.nurrobin.smpstats.throwback;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.nurrobin.smpstats.throwback.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

class TrophySelectorTest {

    /** 20 players; player 1 is first in everything he does, the others fill the ranks. */
    private TrophyBoard crowdedBoard(Map<String, Double> own) {
        List<PlayerData> all = new ArrayList<>();
        all.add(player(1, "Me", 5 * HOUR, own));
        for (int i = 2; i <= 20; i++) {
            Map<String, Double> m = new java.util.HashMap<>();
            for (Map.Entry<String, Double> e : own.entrySet()) {
                m.put(e.getKey(), e.getValue() * i);
            }
            all.add(player(i, "P" + i, (5 + i) * HOUR, m));
        }
        return new TrophyBoard(TrophyCatalog.standard(), table(all.toArray(new PlayerData[0])));
    }

    @Test
    void alwaysKeepsPlaytimeBlocksAndDistanceThenTheBestByRelativePlace() {
        Map<String, Double> own = stats(CUSTOM + "walk_one_cm", 100_000, "smpstats:blocks_broken", 50,
                CUSTOM + "jump", 100, CUSTOM + "fish_caught", 10, CUSTOM + "eat_cake_slice", 3, CUSTOM + "bell_ring", 4);
        TrophyBoard board = crowdedBoard(own);

        List<Trophy> picked = TrophySelector.select(board.trophies(id(1)), 12);

        assertEquals(List.of("playtime", "blocks_broken", "distance_foot"),
                picked.subList(0, 3).stream().map(t -> t.def().id()).toList());
        assertEquals(7, picked.size(), "only categories with a value above zero are offered");
        assertTrue(picked.stream().noneMatch(t -> t.value().number() <= 0));
    }

    @Test
    void bestNineByRelativePlaceFillTheRest() {
        // player 1 is last in jumps (everyone else has more) but first in fish (everyone else has less)
        List<PlayerData> all = new ArrayList<>();
        Map<String, Double> me = stats(CUSTOM + "jump", 1, CUSTOM + "fish_caught", 1000, CUSTOM + "bell_ring", 5, CUSTOM + "eat_cake_slice", 5);
        all.add(player(1, "Me", 5 * HOUR, me));
        for (int i = 2; i <= 10; i++) {
            all.add(player(i, "P" + i, 5 * HOUR, stats(CUSTOM + "jump", 10 * i, CUSTOM + "fish_caught", i,
                    CUSTOM + "bell_ring", 5 + i, CUSTOM + "eat_cake_slice", 5)));
        }
        TrophyBoard board = new TrophyBoard(TrophyCatalog.standard(), table(all.toArray(new PlayerData[0])));

        List<String> ids = TrophySelector.select(board.trophies(id(1)), 5).stream().map(t -> t.def().id()).toList();

        // playtime is shared by everyone -> still always; fish (place 1) beats cake (tie, place 1 of 10 -> 1/10 too)
        assertEquals("playtime", ids.get(0));
        assertTrue(ids.indexOf("fish") < ids.indexOf("jumps"), ids.toString());
        assertTrue(ids.indexOf("jumps") > ids.indexOf("bell"), ids.toString());
    }

    @Test
    void selectionIsDeterministic() {
        Map<String, Double> own = stats(CUSTOM + "walk_one_cm", 100_000, CUSTOM + "jump", 100, CUSTOM + "fish_caught", 10,
                CUSTOM + "eat_cake_slice", 3, CUSTOM + "bell_ring", 4, CUSTOM + "raid_win", 2, CUSTOM + "sleep_in_bed", 9);
        TrophyBoard board = crowdedBoard(own);

        List<String> first = TrophySelector.select(board.trophies(id(1)), 8).stream().map(t -> t.def().id()).toList();
        for (int i = 0; i < 5; i++) {
            assertEquals(first, TrophySelector.select(board.trophies(id(1)), 8).stream().map(t -> t.def().id()).toList());
        }
    }

    @Test
    void tiesFallBackToPoolOrder() {
        // everyone has identical values, so every relative place is 1/N and the pool order decides
        List<PlayerData> all = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            all.add(player(i, "P" + i, HOUR, stats(CUSTOM + "jump", 5, CUSTOM + "bell_ring", 5, CUSTOM + "fish_caught", 5)));
        }
        TrophyBoard board = new TrophyBoard(TrophyCatalog.standard(), table(all.toArray(new PlayerData[0])));

        List<String> ids = TrophySelector.select(board.trophies(id(1)), 12).stream().map(t -> t.def().id()).toList();

        assertEquals(List.of("playtime", "fish", "bell", "jumps"), ids);
    }

    @Test
    void neverMoreThanTheSlots() {
        Map<String, Double> own = new java.util.HashMap<>(stats(CUSTOM + "walk_one_cm", 100_000, CUSTOM + "jump", 100));
        TrophyBoard board = crowdedBoard(own);

        assertEquals(2, TrophySelector.select(board.trophies(id(1)), 2).size());
    }

    @Test
    void zeroValuesAreDroppedEvenIfSomeoneHandsThemIn() {
        TrophyDef def = new TrophyDef("x", TrophyDef.Kind.HIGHEST, Unit.COUNT, SceneType.JUMP, false, p -> Value.of(0));
        Trophy zero = new Trophy(def, Value.of(0), java.util.Optional.empty(), 0);

        assertTrue(TrophySelector.select(List.of(zero), 12).isEmpty());
    }
}
