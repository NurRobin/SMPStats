package de.nurrobin.smpstats.throwback;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.nurrobin.smpstats.throwback.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

class TrophyBoardTest {
    private static Trophy trophy(List<Trophy> all, String id) {
        return all.stream().filter(t -> t.def().id().equals(id)).findFirst().orElse(null);
    }

    @Test
    void ranksByValueWithSharedPlacesAndOnlyCountsPositiveValues() {
        TrophyBoard board = new TrophyBoard(TrophyCatalog.standard(), table(
                player(1, "Ann", 10 * HOUR, stats(CUSTOM + "jump", 500)),
                player(2, "Ben", 10 * HOUR, stats(CUSTOM + "jump", 500)),
                player(3, "Cem", 10 * HOUR, stats(CUSTOM + "jump", 100)),
                player(4, "Dia", 10 * HOUR, stats(CUSTOM + "jump", 0))));

        Trophy ann = trophy(board.trophies(id(1)), "jumps");
        Trophy cem = trophy(board.trophies(id(3)), "jumps");

        assertEquals(1, ann.rank().orElseThrow().place());
        assertEquals(3, ann.rank().orElseThrow().total(), "the player with 0 jumps does not count");
        assertEquals(3, cem.rank().orElseThrow().place(), "1, 1, 3 after a tie");
        assertNull(trophy(board.trophies(id(4)), "jumps"), "a value of zero produces no trophy");
    }

    @Test
    void earliestAdvancementWins() {
        TrophyBoard board = new TrophyBoard(TrophyCatalog.standard(), table(
                player(1, "Ann", HOUR, stats(), Map.of("minecraft:story/enter_the_nether", 2_000L)),
                player(2, "Ben", HOUR, stats(), Map.of("minecraft:story/enter_the_nether", 1_000L))));

        assertEquals(2, trophy(board.trophies(id(1)), "nether").rank().orElseThrow().place());
        assertEquals(1, trophy(board.trophies(id(2)), "nether").rank().orElseThrow().place());
    }

    @Test
    void favouriteBlockAndNemesisCarryTheTranslationKey() {
        TrophyBoard board = new TrophyBoard(TrophyCatalog.standard(), table(
                player(1, "Ann", HOUR, stats("minecraft:mined/minecraft:stone", 400, "minecraft:mined/minecraft:dirt", 20,
                        "minecraft:killed_by/minecraft:creeper", 7, "minecraft:killed_by/minecraft:zombie", 2))));

        List<Trophy> all = board.trophies(id(1));

        assertEquals("block.minecraft.stone", trophy(all, "fav_block").value().labelKey());
        assertEquals(400.0, trophy(all, "fav_block").value().number());
        assertEquals("entity.minecraft.creeper", trophy(all, "nemesis").value().labelKey());
        assertEquals(420.0, trophy(all, "blocks_broken").value().number(), "falls back to the sum of mined blocks");
    }

    @Test
    void nameLookupIsCaseInsensitive() {
        TrophyBoard board = new TrophyBoard(TrophyCatalog.standard(), table(player(1, "DonMahito", HOUR, stats())));

        assertEquals(id(1), board.playerByName("donmahito").orElseThrow().uuid());
        assertEquals(Optional.empty(), board.playerByName("nobody"));
    }

    @Test
    void distanceAndPlaytimeUseRawUnits() {
        TrophyBoard board = new TrophyBoard(TrophyCatalog.standard(), table(
                player(1, "Ann", 3 * HOUR, stats(CUSTOM + "walk_one_cm", 200_000, CUSTOM + "sprint_one_cm", 100_000))));

        List<Trophy> all = board.trophies(id(1));

        assertEquals(300_000.0, trophy(all, "distance_foot").value().number());
        assertEquals("3,0 km", trophy(all, "distance_foot").def().unit().format(300_000));
        assertEquals(3.0, trophy(all, "playtime").def().unit().display(trophy(all, "playtime").value().number()));
    }
}
