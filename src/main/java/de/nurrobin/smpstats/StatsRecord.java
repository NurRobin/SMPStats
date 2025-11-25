package de.nurrobin.smpstats;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class StatsRecord {
    private final UUID uuid;
    private String name;
    private long firstJoin;
    private long lastJoin;
    private long playtimeMillis;
    private long deaths;
    private String lastDeathCause;
    private long playerKills;
    private long mobKills;
    private long blocksPlaced;
    private long blocksBroken;
    private double distanceOverworld;
    private double distanceNether;
    private double distanceEnd;
    private Set<String> biomesVisited;
    private double damageDealt;
    private double damageTaken;
    private long itemsCrafted;
    private long itemsConsumed;

    public StatsRecord(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.biomesVisited = new LinkedHashSet<>();
    }

    public StatsRecord copy() {
        StatsRecord copy = new StatsRecord(uuid, name);
        copy.firstJoin = firstJoin;
        copy.lastJoin = lastJoin;
        copy.playtimeMillis = playtimeMillis;
        copy.deaths = deaths;
        copy.lastDeathCause = lastDeathCause;
        copy.playerKills = playerKills;
        copy.mobKills = mobKills;
        copy.blocksPlaced = blocksPlaced;
        copy.blocksBroken = blocksBroken;
        copy.distanceOverworld = distanceOverworld;
        copy.distanceNether = distanceNether;
        copy.distanceEnd = distanceEnd;
        copy.biomesVisited = new LinkedHashSet<>(biomesVisited);
        copy.damageDealt = damageDealt;
        copy.damageTaken = damageTaken;
        copy.itemsCrafted = itemsCrafted;
        copy.itemsConsumed = itemsConsumed;
        return copy;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public void setFirstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }

    public long getLastJoin() {
        return lastJoin;
    }

    public void setLastJoin(long lastJoin) {
        this.lastJoin = lastJoin;
    }

    public long getPlaytimeMillis() {
        return playtimeMillis;
    }

    public void addPlaytimeMillis(long millis) {
        this.playtimeMillis += millis;
    }

    public void setPlaytimeMillis(long playtimeMillis) {
        this.playtimeMillis = playtimeMillis;
    }

    public long getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public void setDeaths(long deaths) {
        this.deaths = deaths;
    }

    public String getLastDeathCause() {
        return lastDeathCause;
    }

    public void setLastDeathCause(String lastDeathCause) {
        this.lastDeathCause = lastDeathCause;
    }

    public long getPlayerKills() {
        return playerKills;
    }

    public void incrementPlayerKills() {
        this.playerKills++;
    }

    public void setPlayerKills(long playerKills) {
        this.playerKills = playerKills;
    }

    public long getMobKills() {
        return mobKills;
    }

    public void incrementMobKills() {
        this.mobKills++;
    }

    public void setMobKills(long mobKills) {
        this.mobKills = mobKills;
    }

    public long getBlocksPlaced() {
        return blocksPlaced;
    }

    public void incrementBlocksPlaced() {
        this.blocksPlaced++;
    }

    public void setBlocksPlaced(long blocksPlaced) {
        this.blocksPlaced = blocksPlaced;
    }

    public long getBlocksBroken() {
        return blocksBroken;
    }

    public void incrementBlocksBroken() {
        this.blocksBroken++;
    }

    public void setBlocksBroken(long blocksBroken) {
        this.blocksBroken = blocksBroken;
    }

    public double getDistanceOverworld() {
        return distanceOverworld;
    }

    public void addDistanceOverworld(double distanceOverworld) {
        this.distanceOverworld += distanceOverworld;
    }

    public void setDistanceOverworld(double distanceOverworld) {
        this.distanceOverworld = distanceOverworld;
    }

    public double getDistanceNether() {
        return distanceNether;
    }

    public void addDistanceNether(double distanceNether) {
        this.distanceNether += distanceNether;
    }

    public void setDistanceNether(double distanceNether) {
        this.distanceNether = distanceNether;
    }

    public double getDistanceEnd() {
        return distanceEnd;
    }

    public void addDistanceEnd(double distanceEnd) {
        this.distanceEnd += distanceEnd;
    }

    public void setDistanceEnd(double distanceEnd) {
        this.distanceEnd = distanceEnd;
    }

    public Set<String> getBiomesVisited() {
        if (biomesVisited == null) {
            biomesVisited = new LinkedHashSet<>();
        }
        return biomesVisited;
    }

    public void setBiomesVisited(Set<String> biomesVisited) {
        this.biomesVisited = Objects.requireNonNullElseGet(biomesVisited, LinkedHashSet::new);
    }

    public void addBiome(String biome) {
        getBiomesVisited().add(biome);
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public void addDamageDealt(double damageDealt) {
        this.damageDealt += damageDealt;
    }

    public void setDamageDealt(double damageDealt) {
        this.damageDealt = damageDealt;
    }

    public double getDamageTaken() {
        return damageTaken;
    }

    public void addDamageTaken(double damageTaken) {
        this.damageTaken += damageTaken;
    }

    public void setDamageTaken(double damageTaken) {
        this.damageTaken = damageTaken;
    }

    public long getItemsCrafted() {
        return itemsCrafted;
    }

    public void incrementItemsCrafted(long amount) {
        this.itemsCrafted += amount;
    }

    public void setItemsCrafted(long itemsCrafted) {
        this.itemsCrafted = itemsCrafted;
    }

    public long getItemsConsumed() {
        return itemsConsumed;
    }

    public void incrementItemsConsumed() {
        this.itemsConsumed++;
    }

    public void setItemsConsumed(long itemsConsumed) {
        this.itemsConsumed = itemsConsumed;
    }
}
