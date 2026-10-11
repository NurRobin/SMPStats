package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Size and place of the two wall boards (rules, privacy) and of the rules button. The hall, the renderer and the
 * edit mode all read the numbers from here, so the preview in the edit mode takes up exactly the space the real
 * board will.
 */
final class PanelMetrics {
    /** Wrap width of a board's text in font pixels, before scaling. */
    static final int LINE_WIDTH_PX = 320;
    static final float SCALE = 0.5f;
    /** Room under the rules board for the button; the edit mode keeps it free and the click box fills it. */
    static final double BUTTON_SPACE = 0.6;
    /** The button text starts this far under the board and stands this far in front of it. */
    static final double BUTTON_DROP = 0.4;
    static final double BUTTON_OUT = 0.15;
    static final float CLICK_WIDTH = 1.2f;
    /** Gap between the rules board and the privacy board beside it when the privacy board has no place of its own. */
    static final double BESIDE_GAP = 0.5;
    /** Text display geometry: one block is 40 font pixels wide at scale 1, a line is 9 pixels high. */
    static final double PX_PER_BLOCK = 40.0;
    static final double LINE_HEIGHT_PX = 9.0;
    static final double PADDING_PX = 2.0;
    /** Wrapping is estimated; bold and wide letters make the average a bit generous on purpose. */
    static final double AVG_CHAR_PX = 6.0;
    /** The board is flat; the box around it is a slab this thick (blocks). */
    static final double THICKNESS = 0.2;

    private PanelMetrics() {
    }

    static double width() {
        return LINE_WIDTH_PX * SCALE / PX_PER_BLOCK;
    }

    /** Estimated number of wrapped lines of a board. */
    static int lines(List<Component> text) {
        int total = 0;
        for (Component line : text) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            for (String part : plain.split("\n", -1)) {
                total += Math.max(1, (int) Math.ceil(part.length() * AVG_CHAR_PX / LINE_WIDTH_PX));
            }
        }
        return total;
    }

    /** Height in blocks of a board with the given text. */
    static double height(List<Component> text) {
        return height(text, SCALE);
    }

    static double height(List<Component> text, float scale) {
        return (lines(text) * LINE_HEIGHT_PX + 2 * PADDING_PX) * scale / PX_PER_BLOCK;
    }

    /** Estimated width in blocks of a text drawn at {@code scale}: its longest line, at most the wrap width. */
    static double textWidth(List<Component> text, float scale) {
        int longest = 0;
        for (Component line : text) {
            for (String part : PlainTextComponentSerializer.plainText().serialize(line).split("\n", -1)) {
                longest = Math.max(longest, part.length());
            }
        }
        double px = Math.min(LINE_WIDTH_PX, longest * AVG_CHAR_PX) + 2 * PADDING_PX;
        return px * scale / PX_PER_BLOCK;
    }

    /** The rules board: its place in the layout is its bottom middle, like the privacy board's own place. */
    static Location rulesBoard(World world, Pos rules) {
        return new Location(world, rules.x(), rules.y(), rules.z(), rules.yaw(), 0);
    }

    /** Bottom of the "[Regeln gelesen]" text, just under the rules board and a little in front of it. */
    static Location rulesButton(World world, Pos rules) {
        return forward(world, rules, BUTTON_OUT, -BUTTON_DROP);
    }

    /** Bottom middle of the click box over the button text, reaching out towards the reader. */
    static Location rulesClickBox(World world, Pos rules) {
        return forward(world, rules, CLICK_WIDTH / 2, -BUTTON_SPACE);
    }

    /**
     * Bottom middle of the privacy board: its own place if the layout has one (that place already is the bottom
     * middle, see {@link EditGeometry#board}), else beside the rules board, on the reader's right.
     */
    static Location privacyBoard(World world, Pos rules, Pos privacy) {
        if (privacy != null) {
            return new Location(world, privacy.x(), privacy.y(), privacy.z(), privacy.yaw(), 0);
        }
        double rad = Math.toRadians(rules.yaw());
        double side = width() + BESIDE_GAP;
        return new Location(world, rules.x() + Math.cos(rad) * side, rules.y(), rules.z() + Math.sin(rad) * side,
                rules.yaw(), 0);
    }

    /** A point {@code out} blocks towards the reader of a board and {@code up} blocks above its bottom. */
    private static Location forward(World world, Pos board, double out, double up) {
        double rad = Math.toRadians(board.yaw());
        return new Location(world, board.x() - Math.sin(rad) * out, board.y() + up, board.z() + Math.cos(rad) * out);
    }
}
