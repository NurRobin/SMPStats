package de.nurrobin.smpstats.social;

import java.util.UUID;

public record SocialPairRow(UUID uuidA,
                            UUID uuidB,
                            long seconds,
                            long sharedKills,
                            long sharedPlayerKills,
                            long sharedMobKills) {
}
