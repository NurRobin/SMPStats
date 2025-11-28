package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.moments.MomentService;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MomentListener implements Listener {
    private final MomentService momentService;
    private final de.nurrobin.smpstats.timeline.DeathReplayService deathReplayService;
    private final Map<UUID, Long> lastWaterPlace = new HashMap<>();

    public MomentListener(MomentService momentService, de.nurrobin.smpstats.timeline.DeathReplayService deathReplayService) {
        this.momentService = momentService;
        this.deathReplayService = deathReplayService;
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
        // Capture death replay
        if (deathReplayService != null) {
            deathReplayService.capture(event.getEntity(), cause, event.getEntity().getFallDistance());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        momentService.onDamage(player, event.getFinalDamage(), event.getCause().name());
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getFinalDamage() == 0) {
            Long lastWater = lastWaterPlace.get(player.getUniqueId());
            if (lastWater != null && System.currentTimeMillis() - lastWater < 3000) {
                momentService.onBossKill(player, "MLG_WATER", player.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Material type = event.getItem().getItemStack().getType();
        momentService.onItemGain(player, type, event.getItem().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.WITHER && event.getEntity().getKiller() != null) {
            momentService.onBossKill(event.getEntity().getKiller(), "WITHER", event.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketPlace(PlayerBucketEmptyEvent event) {
        if (event.getBucket() == org.bukkit.Material.WATER_BUCKET) {
            lastWaterPlace.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
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
