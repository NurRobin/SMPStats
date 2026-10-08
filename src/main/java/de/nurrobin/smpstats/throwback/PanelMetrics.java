package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Size and place of the two wall boards (rules, privacy). The hall, the renderer and the edit mode all read the
 * numbers from here, so the preview in the edit mode takes up exactly the space the real board will.
 */
final class PanelMetrics {
    /** Wrap width of a board's text in font pixels, before scaling. */
    static final int LINE_WIDTH_PX = 320;
    static final float SCALE = 0.5f;
    /** The rules board hangs this far to the reader's left of the station, the fallback privacy board to the right. */
    static final double OFFSET = 2.0;
    static final double RULES_Y = 1.2;
    static final double PRIVACY_Y = 1.5;
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
        return (lines(text) * LINE_HEIGHT_PX + 2 * PADDING_PX) * SCALE / PX_PER_BLOCK;
    }

    /** Bottom middle of the rules board next to the station at {@code rules}. */
    static Location rulesBoard(World world, Pos rules) {
        double rad = Math.toRadians(rules.yaw());
        return new Location(world, rules.x() - Math.cos(rad) * OFFSET, rules.y() + RULES_Y,
                rules.z() - Math.sin(rad) * OFFSET, rules.yaw(), 0);
    }

    /** Bottom middle of the privacy board: at its own place if the layout has one, else on the other side of the station. */
    static Location privacyBoard(World world, Pos rules, Pos privacy) {
        if (privacy != null) {
            return new Location(world, privacy.x(), privacy.y() + PRIVACY_Y, privacy.z(), privacy.yaw(), 0);
        }
        double rad = Math.toRadians(rules.yaw());
        return new Location(world, rules.x() + Math.cos(rad) * OFFSET, rules.y() + PRIVACY_Y,
                rules.z() + Math.sin(rad) * OFFSET, rules.yaw(), 0);
    }
}
