package de.nurrobin.smpstats.throwback;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shows the edit boxes and the checklist to one builder; an interface because MockBukkit cannot show what a single player sees. */
interface EditEffects {
    /** A box edge from {@code from} to {@code to}, visible to {@code viewer} only, until {@link Line#remove()}. */
    Line line(Player viewer, Location from, Location to, Color color);

    /** Shows {@code lines} top to bottom in the sidebar of {@code viewer}, replacing what the sidebar showed before. */
    void sidebar(Player viewer, Component title, List<Component> lines);

    /** Gives the viewer back the scoreboard they had before the first {@link #sidebar} call. */
    void clearSidebar(Player viewer);

    interface Line {
        void remove();
    }

    static EditEffects paper(Plugin plugin) {
        return new Paper(plugin);
    }

    /** Lines are thin block displays, the checklist is a scoreboard of its own per builder. */
    final class Paper implements EditEffects {
        static final float THICKNESS = 0.04f;
        private static final String OBJECTIVE = "tb_edit";

        private final Plugin plugin;
        private final Map<UUID, Scoreboard> previous = new ConcurrentHashMap<>();

        Paper(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public Line line(Player viewer, Location from, Location to, Color color) {
            Vector3f dir = new Vector3f((float) (to.getX() - from.getX()), (float) (to.getY() - from.getY()),
                    (float) (to.getZ() - from.getZ()));
            float length = dir.length();
            Quaternionf turn = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), dir.normalize(new Vector3f()));
            // the cube runs from 0 to 1 on every axis; this centres its thin cross-section on the line
            Vector3f shift = turn.transform(new Vector3f(0, -THICKNESS / 2, -THICKNESS / 2));
            Location at = from.clone();
            at.setYaw(0);
            at.setPitch(0);
            BlockDisplay display = from.getWorld().spawn(at, BlockDisplay.class, d -> {
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.setBlock(material(color).createBlockData());
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(shift, turn, new Vector3f(length, THICKNESS, THICKNESS), new Quaternionf()));
            });
            viewer.showEntity(plugin, display);
            return display::remove;
        }

        private static Material material(Color color) {
            if (color.equals(ThrowbackEditor.BLOCKED)) {
                return Material.RED_CONCRETE;
            }
            if (color.equals(ThrowbackEditor.FLOOR)) {
                return Material.LIGHT_BLUE_CONCRETE;
            }
            return Material.LIME_CONCRETE;
        }

        @Override
        public void sidebar(Player viewer, Component title, List<Component> lines) {
            Scoreboard board = viewer.getScoreboard();
            if (board.getObjective(OBJECTIVE) == null) {
                previous.putIfAbsent(viewer.getUniqueId(), board);
                board = Bukkit.getScoreboardManager().getNewScoreboard();
                viewer.setScoreboard(board);
            }
            Objective objective = board.getObjective(OBJECTIVE);
            if (objective == null) {
                objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
                objective.numberFormat(NumberFormat.blank());
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            } else {
                objective.displayName(title);
            }
            for (String entry : board.getEntries()) {
                if (entry.startsWith("line") && Integer.parseInt(entry.substring(4)) >= lines.size()) {
                    board.resetScores(entry);
                }
            }
            for (int i = 0; i < lines.size(); i++) {
                // the entry only has to be unique; the text shown is the custom name
                Score score = objective.getScore("line" + i);
                score.setScore(lines.size() - i);
                score.customName(lines.get(i));
            }
        }

        @Override
        public void clearSidebar(Player viewer) {
            Scoreboard before = previous.remove(viewer.getUniqueId());
            if (before != null && viewer.isOnline()) {
                viewer.setScoreboard(before);
            }
        }
    }
}
