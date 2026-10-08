package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** {@code /throwback}: visit the hall, plus admin sub-commands to measure in and build the stand-in corridor. */
public final class ThrowbackCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "smpstats.admin";
    private static final String BUILD = "smpstats.throwback.build";
    private static final List<String> ADMIN_SUBS = List.of("info", "reload", "build", "tp", "setstart", "setstage",
            "setrules", "portal1", "portal2", "reset", "skip", "preview", "edit");
    private static final List<String> EDIT_SUBS = List.of("save", "cancel", "text");

    private final Plugin plugin;
    private final ThrowbackService service;
    private final java.util.function.Supplier<ThrowbackSettings> reread;
    private final ThrowbackEditor editor;
    private Location portalCorner;

    public ThrowbackCommand(Plugin plugin, ThrowbackService service, java.util.function.Supplier<ThrowbackSettings> reread,
                            ThrowbackEditor editor) {
        this.plugin = plugin;
        this.service = service;
        this.reread = reread;
        this.editor = editor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "visit" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            // builders without smpstats.admin may use the edit mode and nothing else
            boolean edit = args.length >= 2 && args[1].equalsIgnoreCase("edit") && sender.hasPermission(BUILD);
            if (!sender.hasPermission(ADMIN) && !edit) {
                sender.sendMessage(Component.text("Dafür fehlt dir die Berechtigung.", net.kyori.adventure.text.format.NamedTextColor.RED));
                return true;
            }
            return admin(sender, args);
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur ingame. Admin-Befehle: /throwback admin <" + String.join("|", ADMIN_SUBS) + ">");
            return true;
        }
        switch (sub) {
            case "visit" -> service.visit(p);
            case "confirm" -> service.confirmRules(p, true);
            case "leave" -> service.leaveVisit(p);
            default -> p.sendMessage(Component.text("Nutze: /throwback [leave]"));
        }
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        String sub = args.length < 2 ? "info" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "reload" -> {
                plugin.reloadConfig();
                service.reload(reread.get());
                say(sender, "Throwback-Konfiguration und Texte neu geladen.");
            }
            case "reset" -> reset(sender, args);
            case "preview" -> preview(sender, args);
            case "skip" -> {
                if (args.length < 3) {
                    say(sender, "Nutze: /throwback admin skip <spieler> (Pflicht-Rundgang als erledigt markieren)");
                } else {
                    service.skipTour(Bukkit.getOfflinePlayer(args[2]).getUniqueId());
                    say(sender, "Rundgang von " + args[2] + " als erledigt markiert.");
                }
            }
            default -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Dieser Befehl geht nur ingame.");
                    return true;
                }
                adminAsPlayer(p, sub, args);
            }
        }
        return true;
    }

    private void adminAsPlayer(Player p, String sub, String[] args) {
        switch (sub) {
            case "build" -> {
                World world = service.loadForAdmin();
                if (world == null) {
                    say(p, "Die Hallen-Welt konnte nicht geladen werden, siehe Server-Log.");
                    return;
                }
                HallLayout layout = PlaceholderHall.build(world);
                service.updateLayout(old -> layout);
                say(p, "Platzhalter-Gang gebaut und Layout gespeichert (" + layout.stages().size()
                        + " Bühnen). Mit /throwback admin tp kommst du hin.");
            }
            case "tp" -> {
                World world = service.loadForAdmin();
                Pos start = service.settings().layout().start();
                if (world == null) {
                    say(p, "Die Hallen-Welt konnte nicht geladen werden.");
                } else {
                    p.teleportAsync(start == null ? new Location(world, 0.5, 64, 0.5) : start.at(world));
                }
            }
            case "setstart" -> {
                if (inHall(p)) {
                    service.updateLayout(l -> l.withStart(Pos.of(p.getLocation())));
                    say(p, "Start gesetzt.");
                }
            }
            case "setrules" -> {
                if (inHall(p)) {
                    // the board hangs two blocks in front of the admin at eye level and faces them
                    Location at = p.getLocation();
                    float yaw = EditGeometry.snapYaw(at.getYaw());
                    double rad = Math.toRadians(yaw);
                    Pos board = new Pos(at.getX() - Math.sin(rad) * 2, at.getY() + 1.2, at.getZ() + Math.cos(rad) * 2,
                            EditGeometry.towardViewerYaw(yaw));
                    service.updateLayout(l -> l.withRules(board));
                    say(p, "Regeltafel gesetzt, zwei Blöcke vor dir, der Knopf direkt darunter.");
                }
            }
            case "setstage" -> setStage(p, args);
            case "portal1" -> {
                if (inHall(p)) {
                    portalCorner = p.getLocation().getBlock().getLocation();
                    say(p, "Portal-Ecke 1 gemerkt. Jetzt /throwback admin portal2.");
                }
            }
            case "portal2" -> {
                if (inHall(p)) {
                    if (portalCorner == null || portalCorner.getWorld() != p.getWorld()) {
                        say(p, "Erst /throwback admin portal1.");
                        return;
                    }
                    Location b = p.getLocation().getBlock().getLocation();
                    Region region = Region.of(portalCorner.getBlockX(), portalCorner.getBlockY(), portalCorner.getBlockZ(),
                            b.getBlockX(), b.getBlockY(), b.getBlockZ());
                    service.updateLayout(l -> l.withPortal(region));
                    say(p, "Portal-Bereich gesetzt.");
                }
            }
            case "edit" -> edit(p, args);
            default -> say(p, "Nutze: /throwback admin <" + String.join("|", ADMIN_SUBS) + ">");
        }
    }

    private void edit(Player p, String[] args) {
        String action = args.length < 3 ? "" : args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "" -> editor.begin(p);
            case "save" -> editor.save(p);
            case "cancel" -> editor.cancel(p);
            case "text" -> editor.setText(p, args.length < 4 ? null
                    : String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
            default -> say(p, "Nutze: /throwback admin edit [" + String.join("|", EDIT_SUBS) + "]");
        }
    }

    private boolean inHall(Player p) {
        if (!service.isHallWorld(p.getWorld())) {
            say(p, "Das geht nur in der Hallen-Welt (/throwback admin tp).");
            return false;
        }
        return true;
    }

    private void setStage(Player p, String[] args) {
        if (!inHall(p)) {
            return;
        }
        if (args.length < 3) {
            say(p, "Nutze: /throwback admin setstage <1-" + HallLayout.MAX_STAGES + "> (Position und Blickrichtung zur Rückwand der Bühne)");
            return;
        }
        try {
            int n = Integer.parseInt(args[2]);
            Pos pos = new Pos(p.getLocation().getBlockX() + 0.5, p.getLocation().getBlockY(), p.getLocation().getBlockZ() + 0.5,
                    p.getLocation().getYaw());
            service.updateLayout(l -> l.withStage(n - 1, pos));
            say(p, "Bühne " + n + " gesetzt.");
        } catch (NumberFormatException e) {
            say(p, "Bühnennummer erwartet.");
        } catch (IllegalArgumentException e) {
            say(p, e.getMessage());
        }
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            say(sender, "Nutze: /throwback admin reset <spieler>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        UUID uuid = target.getUniqueId();
        service.resetProgress(uuid);
        say(sender, "Fortschritt von " + args[2] + " gelöscht: Beim nächsten Join gibt es wieder den Pflicht-Rundgang.");
    }

    private void preview(CommandSender sender, String[] args) {
        if (args.length < 3) {
            say(sender, "Nutze: /throwback admin preview <name> (Name wie in der Saison)");
            return;
        }
        HallPlan plan = service.previewPlan(args[2]);
        say(sender, args[2] + ": " + plan.audience() + ", " + plan.stages().size() + " Bühnen");
        for (StagePlan stage : plan.stages()) {
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(stage.text()).replace("\n", " | ");
            say(sender, (stage.index() + 1) + " [" + stage.scene() + "] " + text);
        }
    }

    private void info(CommandSender sender) {
        ThrowbackSettings s = service.settings();
        HallLayout l = s.layout();
        say(sender, "Throwback: " + (s.enabled() ? "an" : "aus") + ", Welt " + s.world() + ", Saison " + s.season()
                + ", Regeln-Version " + s.rulesVersion());
        say(sender, "Layout: Start " + (l.start() != null) + ", Regeltafel " + (l.rules() != null) + ", Portal " + (l.portal() != null)
                + ", Bühnen " + l.stages().size() + "/" + HallLayout.MAX_STAGES + ", Texte " + l.texts().size()
                + (l.usable() ? " (einsatzbereit)" : " (unvollständig, die Halle ist aus)"));
    }

    private static void say(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, net.kyori.adventure.text.format.NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("leave", "confirm"));
            if (sender.hasPermission(ADMIN) || sender.hasPermission(BUILD)) {
                out.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            if (sender.hasPermission(ADMIN)) {
                out.addAll(ADMIN_SUBS);
            } else if (sender.hasPermission(BUILD)) {
                out.add("edit");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("edit")
                && (sender.hasPermission(ADMIN) || sender.hasPermission(BUILD))) {
            out.addAll(EDIT_SUBS);
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(prefix));
        return out;
    }
}
