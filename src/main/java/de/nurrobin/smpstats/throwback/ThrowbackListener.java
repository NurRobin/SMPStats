package de.nurrobin.smpstats.throwback;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/** Bukkit events of the hall: join and quit, world changes, the rules button, and the hall's house rules. */
public final class ThrowbackListener implements Listener {
    private final Plugin plugin;
    private final ThrowbackService service;
    private final MannequinSceneRenderer renderer;

    public ThrowbackListener(Plugin plugin, ThrowbackService service, MannequinSceneRenderer renderer) {
        this.plugin = plugin;
        this.service = service;
        this.renderer = renderer;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            service.preLogin(event.getUniqueId());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Throwback: Vorladen für " + event.getName() + " fehlgeschlagen", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // one tick later, so teleports of other join handlers (homes, spawn plugins) are settled
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                service.onJoin(p);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.onWorldChanged(event.getPlayer(), event.getFrom());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (service.blocksLeaving(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(service.texts().message("portal_blocked"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (handleButton(event.getPlayer(), entity)) {
            event.setCancelled(true);
        } else if (entity instanceof ItemFrame && service.isHallWorld(entity.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            Entity target = event.getEntity();
            if (handleButton(p, target)) {
                event.setCancelled(true);
            } else if (service.isHallWorld(target.getWorld())) {
                event.setCancelled(true);
            }
        }
    }

    private boolean handleButton(Player p, Entity entity) {
        if (!(entity instanceof Interaction)) {
            return false;
        }
        String action = entity.getPersistentDataContainer().get(renderer.actionKey(), PersistentDataType.STRING);
        String owner = entity.getPersistentDataContainer().get(renderer.ownerKey(), PersistentDataType.STRING);
        if (action == null) {
            return false;
        }
        if (owner != null && owner.equals(p.getUniqueId().toString()) && ThrowbackService.ACTION_RULES.equals(action)) {
            service.confirmRules(p, false);
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && service.isHallWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && service.isHallWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (service.inHall(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
