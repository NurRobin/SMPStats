package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.nurrobin.smpstats.throwback.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

class ThrowbackContentTest {
    private static final ThrowbackTexts TEXTS = ThrowbackTexts.defaults();

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private ThrowbackContent content(boolean matchByName, PlayerData... players) {
        return new ThrowbackContent(new TrophyBoard(TrophyCatalog.standard(), table(players)), TEXTS, 60 * 60_000L, 15,
                "2025", "https://example.org/datenschutz", "2026-1", matchByName);
    }

    private PlayerData veteran(int n, String name, double blocks) {
        return player(n, name, 30 * HOUR, stats("smpstats:blocks_broken", blocks, CUSTOM + "walk_one_cm", 5_000_000,
                CUSTOM + "jump", 800, CUSTOM + "fish_caught", 40, CUSTOM + "bell_ring", 12, CUSTOM + "eat_cake_slice", 9,
                CUSTOM + "mob_kills", 300, CUSTOM + "deaths", 25, CUSTOM + "traded_with_villager", 70,
                "minecraft:mined/minecraft:stone", blocks, "minecraft:killed_by/minecraft:creeper", 6));
    }

    @Test
    void playerAboveTheThresholdGetsTwelveStagesWithTrophiesAndInfoFillers() {
        ThrowbackContent content = content(false, veteran(1, "Vera", 20_000));

        HallPlan plan = content.plan(id(1), "Vera", 12);

        assertEquals(Audience.TROPHY, plan.audience());
        assertEquals(12, plan.stages().size());
        long trophyStages = plan.stages().stream().filter(s -> s.scene() != SceneType.INFO).count();
        assertTrue(trophyStages >= 3 && trophyStages <= 12, "trophies: " + trophyStages);
        assertNotNull(plan.statueLabel());
        assertTrue(plain(plan.stages().get(0).text()).contains("30"), plain(plan.stages().get(0).text()));
    }

    @Test
    void zeroValuesNeverAppearInTheTexts() {
        ThrowbackContent content = content(false,
                player(1, "Zed", 5 * HOUR, stats("smpstats:blocks_broken", 100, CUSTOM + "jump", 0, CUSTOM + "bell_ring", 0)));

        HallPlan plan = content.plan(id(1), "Zed", 12);

        for (StagePlan stage : plan.stages()) {
            String text = plain(stage.text());
            assertFalse(text.contains("0×"), text);
            assertFalse(text.toLowerCase().contains("0 sprünge"), text);
        }
        assertEquals(2, plan.stages().stream().filter(s -> s.scene() != SceneType.INFO).count(),
                "only playtime and blocks have a value, the other 10 stages hold event info");
        assertEquals(12, plan.stages().size());
    }

    @Test
    void rankBadgeOnlyForTheTopFifteen() {
        List<PlayerData> all = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            all.add(veteran(i, "P" + i, 1_000.0 * i));
        }
        ThrowbackContent content = content(false, all.toArray(new PlayerData[0]));

        String best = plain(content.plan(id(20), "P20", 12).stages().get(1).text());
        String fifteenth = plain(content.plan(id(6), "P6", 12).stages().get(1).text());
        String sixteenth = plain(content.plan(id(5), "P5", 12).stages().get(1).text());

        assertTrue(best.contains("Platz 1 von 20"), best);
        assertTrue(fifteenth.contains("Platz 15 von 20"), fifteenth);
        assertFalse(sixteenth.contains("Platz"), sixteenth);
    }

    @Test
    void shortPlaytimeGetsTheNewcomerTourWithANeutralWelcomeBack() {
        ThrowbackContent content = content(false, player(1, "Kurz", 20 * 60_000L, stats(CUSTOM + "jump", 50)));

        HallPlan plan = content.plan(id(1), "Kurz", 12);

        assertEquals(Audience.RETURNING, plan.audience());
        assertTrue(plan.stages().stream().allMatch(s -> s.scene() == SceneType.INFO), "no trophies below the threshold");
        assertNull(plan.statueLabel());
        String welcome = plan.welcome().stream().map(ThrowbackContentTest::plain).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(welcome.contains("Willkommen zurück, Kurz"), welcome);
        String lower = welcome.toLowerCase();
        assertFalse(lower.contains("wenig") || lower.contains("kurz ") || lower.contains("stunde") || lower.contains("minute"), welcome);
    }

    @Test
    void thresholdIsInclusive() {
        ThrowbackContent content = content(false, player(1, "Genau", 60 * 60_000L, stats(CUSTOM + "jump", 50)),
                player(2, "Knapp", 60 * 60_000L - 1, stats(CUSTOM + "jump", 50)));

        assertEquals(Audience.TROPHY, content.plan(id(1), "Genau", 12).audience());
        assertEquals(Audience.RETURNING, content.plan(id(2), "Knapp", 12).audience());
    }

    @Test
    void unknownPlayerGetsEventInfoAndRules() {
        ThrowbackContent content = content(false, veteran(1, "Vera", 1_000));

        HallPlan plan = content.plan(UUID.randomUUID(), "Neuling", 12);

        assertEquals(Audience.NEWCOMER, plan.audience());
        assertEquals(12, plan.stages().size());
        assertTrue(plain(plan.rules().get(0)).contains("Regeln"));
        assertTrue(plan.privacy().stream().map(ThrowbackContentTest::plain).anyMatch(l -> l.contains("https://example.org/datenschutz")));
    }

    @Test
    void nameMatchingOnlyWhenEnabled() {
        PlayerData vera = veteran(1, "Vera", 1_000);

        assertEquals(Audience.NEWCOMER, content(false, vera).plan(UUID.randomUUID(), "vera", 12).audience());
        assertEquals(Audience.TROPHY, content(true, vera).plan(UUID.randomUUID(), "vera", 12).audience());
    }

    @Test
    void fewerStagesThanTwelveAreRespected() {
        HallPlan plan = content(false, veteran(1, "Vera", 1_000)).plan(id(1), "Vera", 5);

        assertEquals(5, plan.stages().size());
    }

    @Test
    void nemesisTextUsesATranslatableBlockOrMobName() {
        HallPlan plan = content(false, veteran(1, "Vera", 1_000)).plan(id(1), "Vera", 12);

        boolean found = plan.stages().stream().anyMatch(s -> s.scene() == SceneType.NEMESIS
                && s.text().toString().contains("entity.minecraft.creeper"));
        assertTrue(found, "nemesis stage should carry the creeper translation key");
    }

    @Test
    void everyCatalogEntryHasATitleInTheDefaultTexts() {
        Map<String, Double> all = new HashMap<>();
        for (String key : TrophyCatalog.exactKeys()) {
            all.put(key, 123_456.0);
        }
        all.put("minecraft:mined/minecraft:stone", 5000.0);
        all.put("minecraft:killed_by/minecraft:zombie", 5.0);
        PlayerData p = player(1, "All", 100 * HOUR, all,
                Map.of("minecraft:story/enter_the_nether", 1_700_000_000_000L, "minecraft:story/enter_the_end", 1_701_000_000_000L));
        TrophyBoard board = new TrophyBoard(TrophyCatalog.standard(), table(p));

        for (Trophy t : board.trophies(id(1))) {
            String text = plain(TEXTS.trophy(t, 15));
            assertFalse(text.isBlank(), t.def().id());
            assertFalse(text.contains("<"), "unreplaced placeholder in " + t.def().id() + ": " + text);
        }
        assertEquals(TrophyCatalog.standard().defs().size(), board.trophies(id(1)).size());
    }
}
