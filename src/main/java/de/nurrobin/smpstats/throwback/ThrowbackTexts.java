package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Wording of the hall, read from {@code throwback/texts.yml} (MiniMessage). Missing keys fall back to the defaults. */
public final class ThrowbackTexts {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public record Info(String title, List<String> lines) {
    }

    private final YamlConfiguration yaml;
    private final YamlConfiguration defaults;

    private ThrowbackTexts(YamlConfiguration yaml, YamlConfiguration defaults) {
        this.yaml = yaml;
        this.defaults = defaults;
    }

    /** Loads the file, copying the bundled default next to it first if it does not exist yet. */
    public static ThrowbackTexts load(Path file, Logger logger) {
        YamlConfiguration defaults = bundled();
        if (!Files.exists(file)) {
            try (InputStream in = ThrowbackTexts.class.getResourceAsStream("/throwback/texts.yml")) {
                if (in != null) {
                    Files.createDirectories(file.getParent());
                    Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                logger.warning("Throwback: texts.yml konnte nicht angelegt werden: " + e.getMessage());
            }
        }
        YamlConfiguration yaml = new YamlConfiguration();
        if (Files.exists(file)) {
            try {
                yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                logger.warning("Throwback: texts.yml nicht lesbar (" + e.getMessage() + "), Standardtexte werden genutzt");
                yaml = new YamlConfiguration();
            }
        }
        return new ThrowbackTexts(yaml, defaults);
    }

    public static ThrowbackTexts defaults() {
        return new ThrowbackTexts(new YamlConfiguration(), bundled());
    }

    private static YamlConfiguration bundled() {
        YamlConfiguration cfg = new YamlConfiguration();
        try (InputStream in = ThrowbackTexts.class.getResourceAsStream("/throwback/texts.yml")) {
            if (in != null) {
                cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException("throwback/texts.yml im Jar ist ungültig", e);
        }
        return cfg;
    }

    private String string(String path) {
        String v = yaml.getString(path);
        return v != null ? v : defaults.getString(path, "");
    }

    private List<String> strings(String path) {
        if (yaml.isList(path)) {
            return yaml.getStringList(path);
        }
        return defaults.getStringList(path);
    }

    public static Component render(String template, Map<String, Component> placeholders) {
        TagResolver.Builder resolver = TagResolver.builder();
        placeholders.forEach((k, v) -> resolver.resolver(Placeholder.component(k, v)));
        return MM.deserialize(template, resolver.build());
    }

    public static Component render(String template) {
        return MM.deserialize(template);
    }

    private static Component valueComponent(Trophy t) {
        return Component.text(t.def().unit().format(t.value().number()));
    }

    /** Title, comparison and (for the top places) the badge of one trophy. */
    public Component trophy(Trophy trophy, int badgeTop) {
        String id = trophy.def().id();
        Map<String, Component> ph = new java.util.HashMap<>();
        ph.put("value", valueComponent(trophy));
        ph.put("label", trophy.value().labelKey() == null ? Component.empty() : Component.translatable(trophy.value().labelKey()));
        List<Component> lines = new ArrayList<>();
        lines.add(render(string("trophies." + id + ".title"), ph));
        String compare = compareLine(trophy);
        if (compare != null) {
            lines.add(compare(compare, trophy));
        }
        if (trophy.showsBadge(badgeTop)) {
            lines.add(render(string("badge"), Map.of("place", Component.text(trophy.rank().get().place()),
                    "total", Component.text(trophy.rank().get().total()))));
        }
        return join(lines);
    }

    private String compareLine(Trophy trophy) {
        String base = "trophies." + trophy.def().id() + ".compare";
        double per = firstDouble(base + ".per");
        if (per <= 0) {
            return null;
        }
        double n = trophy.def().unit().display(trophy.value().number()) / per;
        if (n >= 1) {
            return string(base + ".text");
        }
        String small = string(base + ".small");
        return small.isEmpty() ? null : small;
    }

    private Component compare(String template, Trophy trophy) {
        String base = "trophies." + trophy.def().id() + ".compare";
        double n = trophy.def().unit().display(trophy.value().number()) / firstDouble(base + ".per");
        return render(template, Map.of("n", Component.text(Unit.number(n)),
                "p", Component.text(Unit.number(Math.min(100, n * 100))),
                "value", valueComponent(trophy)));
    }

    private double firstDouble(String path) {
        if (yaml.isSet(path)) {
            return yaml.getDouble(path);
        }
        return defaults.getDouble(path, 0);
    }

    private static Component join(List<Component> lines) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out = out.appendNewline();
            }
            out = out.append(lines.get(i));
        }
        return out;
    }

    public static Component joinLines(List<Component> lines) {
        return join(lines);
    }

    public Component statue(String year) {
        return render(string("welcome.statue"), Map.of("season", Component.text(year)));
    }

    public List<Component> welcome(Audience audience, String name) {
        String key = switch (audience) {
            case TROPHY -> "welcome.trophy";
            case RETURNING -> "welcome.returning";
            case NEWCOMER -> "welcome.newcomer";
        };
        return strings(key).stream().map(l -> render(l, Map.of("name", Component.text(name)))).toList();
    }

    public List<Component> rules(String rulesVersion) {
        List<Component> out = new ArrayList<>();
        out.add(render(string("rules.title"), Map.of("version", Component.text(rulesVersion))));
        strings("rules.lines").forEach(l -> out.add(render(l)));
        return out;
    }

    public List<Component> privacy(String url) {
        return strings("rules.privacy").stream()
                .map(l -> render(l, Map.of("url", Component.text(url)))).toList();
    }

    public Component button() {
        return render(string("rules.button"));
    }

    public List<Info> infos() {
        List<Info> out = new ArrayList<>();
        ConfigurationSection src = yaml.isList("info") ? yaml : defaults;
        for (Map<?, ?> m : src.getMapList("info")) {
            Object title = m.get("title");
            Object lines = m.get("lines");
            List<String> l = new ArrayList<>();
            if (lines instanceof List<?> list) {
                list.forEach(o -> l.add(String.valueOf(o)));
            }
            out.add(new Info(title == null ? "" : String.valueOf(title), l));
        }
        return out;
    }

    public Component info(Info info) {
        List<Component> lines = new ArrayList<>();
        lines.add(render(info.title()));
        info.lines().forEach(l -> lines.add(render(l)));
        return join(lines);
    }

    public Component message(String key, Map<String, Component> placeholders) {
        return render(string("messages." + key), placeholders);
    }

    public Component message(String key) {
        return message(key, Map.of());
    }

    /** Debug helper for tests: the raw template of a message. */
    public String rawMessage(String key) {
        return string("messages." + key).toLowerCase(Locale.ROOT);
    }
}
