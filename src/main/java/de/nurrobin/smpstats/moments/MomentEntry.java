package de.nurrobin.smpstats.moments;

import org.bukkit.Location;

import java.util.UUID;

public class MomentEntry {
    private final Long id;
    private final UUID playerId;
    private final MomentType type;
    private final String title;
    private final String detail;
    private final String payload;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final long startedAt;
    private final long endedAt;

    public MomentEntry(Long id,
                       UUID playerId,
                       MomentType type,
                       String title,
                       String detail,
                       String payload,
                       String world,
                       int x,
                       int y,
                       int z,
                       long startedAt,
                       long endedAt) {
        this.id = id;
        this.playerId = playerId;
        this.type = type;
        this.title = title;
        this.detail = detail;
        this.payload = payload;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public static MomentEntry fromLocation(UUID playerId, MomentType type, String title, String detail, String payload, Location location, long startedAt, long endedAt) {
        return new MomentEntry(null, playerId, type, title, detail, payload,
                location.getWorld() != null ? location.getWorld().getName() : "unknown",
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                startedAt, endedAt);
    }

    public Long getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public MomentType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getPayload() {
        return payload;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getEndedAt() {
        return endedAt;
    }
}
