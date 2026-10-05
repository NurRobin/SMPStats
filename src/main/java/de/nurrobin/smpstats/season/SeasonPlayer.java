package de.nurrobin.smpstats.season;

import java.util.UUID;

public record SeasonPlayer(UUID uuid, String name, long playtimeMs) {
}
