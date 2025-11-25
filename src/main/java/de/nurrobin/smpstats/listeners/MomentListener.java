package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.moments.MomentService;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.projectiles.ProjectileSource;

public class MomentListener implements Listener {
    private final MomentService momentService;

    public MomentListener(MomentService momentService) {
        this.momentService = momentService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        momentService.onBlockBreak(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        EntityDamageEvent last = event.getEntity().getLastDamageCause();
        String cause = last != null ? last.getCause().name() : "UNKNOWN";
        boolean selfExplosion = isSelfExplosion(last, event.getEntity());
        momentService.onDeath(event.getEntity(), event.getEntity().getLocation(), event.getEntity().getFallDistance(), cause, selfExplosion);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        momentService.onDamage(player, event.getFinalDamage(), event.getCause().name());
    }

    private boolean isSelfExplosion(EntityDamageEvent event, Player player) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        if (byEntity.getDamager() instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            return source != null && source.getUniqueId().equals(player.getUniqueId());
        }
        return false;
    }
}
