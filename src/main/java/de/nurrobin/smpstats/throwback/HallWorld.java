package de.nurrobin.smpstats.throwback;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * The void world of the hall. It is created or loaded on demand and unloaded again after the last visitor left,
 * so it does not sit in memory (or tick) the whole event. Mobs, weather, time and damage are switched off.
 */
public final class HallWorld implements HallWorlds {
    /** Marks every entity the hall spawned, so leftovers can be found by tag. */
    public static NamespacedKey tagKey(Plugin plugin) {
        return new NamespacedKey(plugin, "throwback");
    }

    private final Plugin plugin;
    private final Logger logger;
    private volatile String name;
    private volatile long time;

    public HallWorld(Plugin plugin, String name, long time) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.name = name;
        this.time = time;
    }

    @Override
    public void configure(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean isHall(World world) {
        return world != null && world.getName().equals(name);
    }

    /** The loaded world or null. */
    @Override
    public World loaded() {
        return Bukkit.getWorld(name);
    }

    /** Loads the world, creating it empty on first use. Main thread only. */
    @Override
    public World load() {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        WorldCreator creator = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new VoidGenerator())
                .generateStructures(false);
        World world = creator.createWorld();
        if (world == null) {
            logger.severe("Throwback: Welt " + name + " konnte nicht geladen werden");
            return null;
        }
        apply(world);
        removeLeftovers(world);
        logger.info("Throwback: Hallen-Welt " + name + " geladen");
        return world;
    }

    private void apply(World world) {
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.SPAWN_WARDENS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.FIRE_DAMAGE, false);
        world.setGameRule(GameRules.DROWNING_DAMAGE, false);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.RAIDS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setTime(time);
        world.setStorm(false);
        world.setThundering(false);
        world.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        world.setKeepSpawnInMemory(false);
    }

    /** Removes entities left over from a crash; the hall never stores its entities (they are not persistent). */
    private void removeLeftovers(World world) {
        NamespacedKey key = tagKey(plugin);
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Throwback: " + removed + " verwaiste Hallen-Entities entfernt");
        }
    }

    /** Unloads the world and saves its blocks. Returns false if players are still inside. */
    @Override
    public boolean unload() {
        World world = loaded();
        if (world == null) {
            return true;
        }
        if (!world.getPlayers().isEmpty()) {
            return false;
        }
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(tagKey(plugin), PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
        boolean ok = Bukkit.unloadWorld(world, true);
        if (ok) {
            logger.info("Throwback: Hallen-Welt " + name + " entladen (niemand mehr drin)");
        } else {
            logger.warning("Throwback: Welt " + name + " ließ sich nicht entladen");
        }
        return ok;
    }
}
