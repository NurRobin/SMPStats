package de.nurrobin.smpstats.commands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatFieldTest {

    @Test
    void resolvesFromNameOrKeyAndProvidesKeys() {
        Optional<StatField> byName = StatField.fromString("DEATHS");
        Optional<StatField> byKey = StatField.fromString("items_crafted");

        assertEquals(StatField.DEATHS, byName.orElseThrow());
        assertEquals(StatField.ITEMS_CRAFTED, byKey.orElseThrow());

        List<String> keys = StatField.keys();
        assertTrue(keys.contains("playtime_ms"));
        assertTrue(keys.contains("items_consumed"));
    }
}
