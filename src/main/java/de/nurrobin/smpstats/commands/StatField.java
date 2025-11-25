package de.nurrobin.smpstats.commands;

import de.nurrobin.smpstats.StatsRecord;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum StatField {
    PLAYTIME("playtime_ms") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setPlaytimeMillis((long) value);
        }
    },
    DEATHS("deaths") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setDeaths((long) value);
        }
    },
    PLAYER_KILLS("player_kills") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setPlayerKills((long) value);
        }
    },
    MOB_KILLS("mob_kills") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setMobKills((long) value);
        }
    },
    BLOCKS_PLACED("blocks_placed") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setBlocksPlaced((long) value);
        }
    },
    BLOCKS_BROKEN("blocks_broken") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setBlocksBroken((long) value);
        }
    },
    DIST_OVERWORLD("dist_overworld") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setDistanceOverworld(value);
        }
    },
    DIST_NETHER("dist_nether") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setDistanceNether(value);
        }
    },
    DIST_END("dist_end") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setDistanceEnd(value);
        }
    },
    DAMAGE_DEALT("damage_dealt") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setDamageDealt(value);
        }
    },
    DAMAGE_TAKEN("damage_taken") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setDamageTaken(value);
        }
    },
    ITEMS_CRAFTED("items_crafted") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setItemsCrafted((long) value);
        }
    },
    ITEMS_CONSUMED("items_consumed") {
        @Override
        public void apply(StatsRecord record, double value) {
            record.setItemsConsumed((long) value);
        }
    };

    private final String key;

    StatField(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public abstract void apply(StatsRecord record, double value);

    public static Optional<StatField> fromString(String raw) {
        if (raw == null) return Optional.empty();
        String normalized = raw.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(f -> f.name().equalsIgnoreCase(normalized) || f.key.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static List<String> keys() {
        return Arrays.stream(values()).map(StatField::key).toList();
    }
}
