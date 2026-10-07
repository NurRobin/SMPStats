package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

import static de.nurrobin.smpstats.throwback.TestData.*;
import static org.junit.jupiter.api.Assertions.*;

class ThrowbackTextsTest {
    @TempDir
    Path tempDir;

    private static String plain(net.kyori.adventure.text.Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static Trophy blocks(double n) {
        TrophyDef def = TrophyCatalog.standard().defs().get(1);
        return new Trophy(def, Value.of(n), Optional.empty(), 1);
    }

    @Test
    void comparisonShowsFromOneUnitOn() {
        ThrowbackTexts texts = ThrowbackTexts.defaults();

        assertTrue(plain(texts.trophy(blocks(157 * 4), 15)).contains("4,0× der Kölner Dom"), plain(texts.trophy(blocks(157 * 4), 15)));
        assertFalse(plain(texts.trophy(blocks(100), 15)).contains("Kölner Dom"), "under one Dom there is no comparison");
    }

    @Test
    void firstStartWritesTheDefaultFileAndLaterEditsWin() throws Exception {
        Path file = tempDir.resolve("throwback/texts.yml");

        ThrowbackTexts.load(file, Logger.getLogger("test"));
        assertTrue(Files.exists(file));

        Files.writeString(file, "trophies:\n  blocks_broken:\n    title: \"<white>Eigener Text <value>\"\n");
        ThrowbackTexts texts = ThrowbackTexts.load(file, Logger.getLogger("test"));

        assertTrue(plain(texts.trophy(blocks(500), 15)).startsWith("Eigener Text 500"), plain(texts.trophy(blocks(500), 15)));
        assertFalse(texts.infos().isEmpty(), "missing keys fall back to the bundled defaults");
    }

    @Test
    void brokenFileFallsBackToDefaults() throws Exception {
        Path file = tempDir.resolve("texts.yml");
        Files.writeString(file, "trophies: [unclosed\n");

        ThrowbackTexts texts = ThrowbackTexts.load(file, Logger.getLogger("test"));

        assertFalse(plain(texts.trophy(blocks(500), 15)).isBlank());
    }

    @Test
    void rulesAndPrivacyHintAreSeparate() {
        ThrowbackTexts texts = ThrowbackTexts.defaults();

        assertTrue(plain(texts.rules("2026-1").get(0)).contains("2026-1"));
        assertTrue(texts.rules("x").stream().map(ThrowbackTextsTest::plain).noneMatch(l -> l.contains("Datenschutz")),
                "the privacy notice is its own block, not part of the rules");
        assertTrue(texts.privacy("https://example.org").stream().map(ThrowbackTextsTest::plain).anyMatch(l -> l.contains("https://example.org")));
    }

    @Test
    void twelveInfoBoardsByDefault() {
        assertEquals(12, ThrowbackTexts.defaults().infos().size());
    }

    @Test
    void sceneLibraryCoversEveryScene() {
        for (SceneType type : SceneType.values()) {
            SceneSpec spec = SceneLibrary.spec(type);
            assertNotNull(spec, type.name());
            assertEquals(type == SceneType.INFO, spec.pose() == null, type.name());
        }
    }
}
