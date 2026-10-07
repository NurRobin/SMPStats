package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Counts what would exist per viewer, without any entities. */
final class FakeScenes implements SceneRenderer {
    final List<Session> opened = new ArrayList<>();

    @Override
    public SceneSession open(Player viewer) {
        Session s = new Session(viewer.getUniqueId());
        opened.add(s);
        return s;
    }

    Session lastOf(UUID viewer) {
        Session found = null;
        for (Session s : opened) {
            if (s.viewer.equals(viewer)) {
                found = s;
            }
        }
        return found;
    }

    static final class Session implements SceneSession {
        final UUID viewer;
        int figures;
        int texts;
        int interactions;
        boolean closed;

        Session(UUID viewer) {
            this.viewer = viewer;
        }

        @Override
        public void figure(Location base, SceneSpec spec) {
            figures++;
        }

        @Override
        public void text(Location at, Component text) {
            texts++;
        }

        @Override
        public void interaction(Location at, float width, float height, String action) {
            interactions++;
        }

        @Override
        public int entityCount() {
            return closed ? 0 : figures + texts + interactions;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
