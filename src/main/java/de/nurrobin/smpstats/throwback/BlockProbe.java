package de.nurrobin.smpstats.throwback;

import org.bukkit.World;

/** Asks whether a block is solid; an interface so the box checks run without a world. */
@FunctionalInterface
interface BlockProbe {
    boolean solid(int x, int y, int z);

    static BlockProbe of(World world) {
        return (x, y, z) -> world.getBlockAt(x, y, z).getType().isSolid();
    }
}
