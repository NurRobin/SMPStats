package de.nurrobin.smpstats.throwback;

import org.bukkit.World;

/** A hall that is just a world that is always there, so tests never create or unload real worlds. */
final class FakeHallWorlds implements HallWorlds {
    private final World world;
    boolean unloaded;

    FakeHallWorlds(World world) {
        this.world = world;
    }

    @Override
    public boolean isHall(World candidate) {
        return candidate != null && candidate.getName().equals(world.getName());
    }

    @Override
    public World loaded() {
        return unloaded ? null : world;
    }

    @Override
    public World load() {
        unloaded = false;
        return world;
    }

    @Override
    public boolean unload() {
        unloaded = true;
        return true;
    }

    @Override
    public void configure(String name) {
    }
}
