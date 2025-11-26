package de.nurrobin.smpstats.commands;

import de.nurrobin.smpstats.StatsRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void returnsEmptyForUnknownInput() {
        assertFalse(StatField.fromString(null).isPresent());
        assertFalse(StatField.fromString("does_not_exist").isPresent());
    }

    @ParameterizedTest
    @MethodSource("fieldAssignments")
    void applyWritesValue(StatField field, double value, Function<StatsRecord, Number> getter, Number expected) {
        StatsRecord record = new StatsRecord(UUID.randomUUID(), "Alex");

        field.apply(record, value);

        assertEquals(expected.doubleValue(), getter.apply(record).doubleValue(), 0.0001, "field " + field);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> fieldAssignments() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(StatField.PLAYTIME, 42.0, (Function<StatsRecord, Number>) StatsRecord::getPlaytimeMillis, 42L),
                org.junit.jupiter.params.provider.Arguments.of(StatField.DEATHS, 3.0, (Function<StatsRecord, Number>) StatsRecord::getDeaths, 3L),
                org.junit.jupiter.params.provider.Arguments.of(StatField.PLAYER_KILLS, 7.0, (Function<StatsRecord, Number>) StatsRecord::getPlayerKills, 7L),
                org.junit.jupiter.params.provider.Arguments.of(StatField.MOB_KILLS, 9.0, (Function<StatsRecord, Number>) StatsRecord::getMobKills, 9L),
                org.junit.jupiter.params.provider.Arguments.of(StatField.BLOCKS_PLACED, 5.0, (Function<StatsRecord, Number>) StatsRecord::getBlocksPlaced, 5L),
                org.junit.jupiter.params.provider.Arguments.of(StatField.BLOCKS_BROKEN, 11.0, (Function<StatsRecord, Number>) StatsRecord::getBlocksBroken, 11L),
                org.junit.jupiter.params.provider.Arguments.of(StatField.DIST_OVERWORLD, 12.5, (Function<StatsRecord, Number>) StatsRecord::getDistanceOverworld, 12.5),
                org.junit.jupiter.params.provider.Arguments.of(StatField.DIST_NETHER, 1.5, (Function<StatsRecord, Number>) StatsRecord::getDistanceNether, 1.5),
                org.junit.jupiter.params.provider.Arguments.of(StatField.DIST_END, 2.5, (Function<StatsRecord, Number>) StatsRecord::getDistanceEnd, 2.5),
                org.junit.jupiter.params.provider.Arguments.of(StatField.DAMAGE_DEALT, 18.0, (Function<StatsRecord, Number>) StatsRecord::getDamageDealt, 18.0),
                org.junit.jupiter.params.provider.Arguments.of(StatField.DAMAGE_TAKEN, 19.0, (Function<StatsRecord, Number>) StatsRecord::getDamageTaken, 19.0),
                org.junit.jupiter.params.provider.Arguments.of(StatField.ITEMS_CRAFTED, 4.0, (Function<StatsRecord, Number>) StatsRecord::getItemsCrafted, 4L),
                org.junit.jupiter.params.provider.Arguments.of(StatField.ITEMS_CONSUMED, 6.0, (Function<StatsRecord, Number>) StatsRecord::getItemsConsumed, 6L)
        );
    }
}
