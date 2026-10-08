package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A free text in the hall, for example the title over a picture: {@code pos} is the bottom middle, it hangs flat
 * and is readable from the side its yaw points to. {@code text} is MiniMessage, {@code <br>} breaks the line.
 */
public record HallText(Pos pos, String text) {
    /** Texts are titles, so they are drawn twice as large as the boards. */
    public static final float SCALE = 1.0f;
    public static final int MAX_LENGTH = 256;

    public Component component() {
        return MiniMessage.miniMessage().deserialize(text);
    }

    static List<HallText> readAll(List<Map<?, ?>> list, Logger logger) {
        List<HallText> out = new ArrayList<>();
        for (Map<?, ?> m : list) {
            Object text = m.get("text");
            if (!(m.get("x") instanceof Number x) || !(m.get("y") instanceof Number y) || !(m.get("z") instanceof Number z)
                    || text == null || String.valueOf(text).isBlank()) {
                logger.warning("Throwback: Text ohne Koordinaten oder Inhalt übersprungen: " + m);
                continue;
            }
            float yaw = m.get("yaw") instanceof Number n ? n.floatValue() : 0f;
            out.add(new HallText(new Pos(x.doubleValue(), y.doubleValue(), z.doubleValue(), yaw), String.valueOf(text)));
        }
        return out;
    }

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", pos.x());
        m.put("y", pos.y());
        m.put("z", pos.z());
        m.put("yaw", (double) pos.yaw());
        m.put("text", text);
        return m;
    }
}
