package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Puts scenes up for exactly one player and takes them down again. The hall logic only talks to this
 * interface, so the figure technique (Mannequin today) can change without touching the flow.
 */
public interface SceneRenderer {

    /** Opens a render session whose entities only {@code viewer} can see. */
    SceneSession open(Player viewer);

    interface SceneSession {
        /** A figure with the viewer's skin and its props; {@code yawAtWall} points at the alcove's back wall. */
        void figure(Location base, SceneSpec spec);

        void text(Location at, Component text);

        /**
         * A text that hangs fixed in place instead of turning to the viewer, readable from the side
         * {@code at}'s yaw points to, shrunk by {@code scale}. The location is the bottom middle of the text.
         */
        void panel(Location at, Component text, float scale);

        /** An invisible click target; {@code action} is stored on it and read by the listener. */
        void interaction(Location at, float width, float height, String action);

        /** Number of entities currently alive in this session. */
        int entityCount();

        /** Removes everything this session created. Safe to call twice. */
        void close();
    }
}
