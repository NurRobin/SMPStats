package de.nurrobin.smpstats.throwback;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Where everything stands in the hall world; read from {@code throwback.layout}, measured in by admin commands. */
public record HallLayout(Pos start, List<Pos> stages, Pos rules, Region portal, List<WallDef> walls) {
    public static final int MAX_STAGES = 12;

    public static HallLayout empty() {
        return new HallLayout(null, List.of(), null, null, List.of());
    }

    /** The hall can only run when start, rules station and portal are known. */
    public boolean usable() {
        return start != null && rules != null && portal != null;
    }

    public static HallLayout read(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return empty();
        }
        List<Pos> stages = new ArrayList<>();
        for (java.util.Map<?, ?> m : section.getMapList("stages")) {
            if (stages.size() >= MAX_STAGES) {
                logger.warning("Throwback: mehr als " + MAX_STAGES + " Bühnen in der Config, der Rest wird ignoriert");
                break;
            }
            Number x = (Number) m.get("x");
            Number y = (Number) m.get("y");
            Number z = (Number) m.get("z");
            Number yaw = (Number) m.get("yaw");
            if (x == null || y == null || z == null) {
                logger.warning("Throwback: Bühne ohne Koordinaten übersprungen: " + m);
                continue;
            }
            stages.add(new Pos(x.doubleValue(), y.doubleValue(), z.doubleValue(), yaw == null ? 0f : yaw.floatValue()));
        }
        return new HallLayout(Pos.read(section.getConfigurationSection("start")), List.copyOf(stages),
                Pos.read(section.getConfigurationSection("rules")), Region.read(section.getConfigurationSection("portal")),
                List.copyOf(WallDef.readAll(section, logger)));
    }

    public void write(ConfigurationSection section) {
        section.set("start", null);
        section.set("rules", null);
        section.set("portal", null);
        section.set("stages", null);
        section.set("walls", null);
        if (start != null) {
            start.write(section.createSection("start"));
        }
        if (rules != null) {
            rules.write(section.createSection("rules"));
        }
        if (portal != null) {
            portal.write(section.createSection("portal"));
        }
        List<java.util.Map<String, Object>> st = new ArrayList<>();
        for (Pos p : stages) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("x", p.x());
            m.put("y", p.y());
            m.put("z", p.z());
            m.put("yaw", (double) p.yaw());
            st.add(m);
        }
        section.set("stages", st);
        section.set("walls", walls.stream().map(WallDef::toMap).toList());
    }

    public HallLayout withStart(Pos p) {
        return new HallLayout(p, stages, rules, portal, walls);
    }

    public HallLayout withRules(Pos p) {
        return new HallLayout(start, stages, p, portal, walls);
    }

    public HallLayout withPortal(Region r) {
        return new HallLayout(start, stages, rules, r, walls);
    }

    /** Sets stage {@code index} (0-based); missing earlier stages are not allowed, so the list never has holes. */
    public HallLayout withStage(int index, Pos p) {
        if (index < 0 || index >= MAX_STAGES || index > stages.size()) {
            throw new IllegalArgumentException("Bühne " + (index + 1) + " ist nicht erlaubt, nächste freie ist " + (stages.size() + 1));
        }
        List<Pos> next = new ArrayList<>(stages);
        if (index == next.size()) {
            next.add(p);
        } else {
            next.set(index, p);
        }
        return new HallLayout(start, List.copyOf(next), rules, portal, walls);
    }

    public HallLayout withWall(WallDef wall) {
        List<WallDef> next = new ArrayList<>(walls.stream().filter(w -> !w.name().equals(wall.name())).toList());
        next.add(wall);
        return new HallLayout(start, stages, rules, portal, List.copyOf(next));
    }
}
