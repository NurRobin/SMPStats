package de.nurrobin.smpstats.throwback;

import org.bukkit.World;

/** Loads and unloads the hall world; an interface so the service can be tested without creating real worlds. */
public interface HallWorlds {
    boolean isHall(World world);

    /** The world if it is currently loaded, else null. */
    World loaded();

    /** Loads (or creates) the world; null if that failed. Main thread only. */
    World load();

    /** Unloads the world if nobody is in it. */
    boolean unload();

    void configure(String name);
}
