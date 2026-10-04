package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.moments.MomentService;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MomentListener implements Listener {
    private final MomentService momentService;
    private final de.nurrobin.smpstats.timeline.DeathReplayService deathReplayService;
    /** Minimum fall distance (blocks) at which emptying a water bucket counts as an MLG attempt. */
    static final float MLG_MIN_FALL_DISTANCE = 10f;
    /** How long after emptying the bucket the landing may happen. */
    static final long MLG_WINDOW_MILLIS = 3000;

    /** Players who emptied a water bucket mid-fall, with the time they did so. Main thread only. */
    private final Map<UUID, Long> pendingMlg = new HashMap<>();

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
        pendingMlg.remove(event.getEntity().getUniqueId());
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
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getFinalDamage() > 0) {
            pendingMlg.remove(player.getUniqueId()); // took fall damage: the MLG failed
        }
    }

    /**
     * Resolves a pending MLG on the first move after the fall distance was reset: landing in water
     * resets it while the player is in water, landing on solid ground resets it outside water.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (pendingMlg.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Long placedAt = pendingMlg.get(player.getUniqueId());
        if (placedAt == null) {
            return;
        }
        if (System.currentTimeMillis() - placedAt > MLG_WINDOW_MILLIS) {
            pendingMlg.remove(player.getUniqueId());
            return;
        }
        if (player.getFallDistance() > 0) {
            return; // still falling
        }
        pendingMlg.remove(player.getUniqueId());
        if (player.isInWater() && !player.isDead()) {
            momentService.onBossKill(player, "MLG_WATER", player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pendingMlg.remove(event.getPlayer().getUniqueId());
    }

    boolean hasPendingMlg(UUID playerId) {
        return pendingMlg.containsKey(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item item = event.getItem();
        UUID thrower = item.getThrower();
        if (thrower != null && thrower.equals(player.getUniqueId())) {
            return; // re-collecting your own drops is not a gain
        }
        ItemStack stack = item.getItemStack();
        int pickedUp = stack.getAmount() - event.getRemaining();
        momentService.onItemGain(player, stack.getType(), item.getLocation(), pickedUp);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.WITHER && event.getEntity().getKiller() != null) {
            momentService.onBossKill(event.getEntity().getKiller(), "WITHER", event.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketPlace(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getFallDistance() >= MLG_MIN_FALL_DISTANCE && !player.isInWater()) {
            long now = System.currentTimeMillis();
            pendingMlg.values().removeIf(placedAt -> now - placedAt > MLG_WINDOW_MILLIS);
            pendingMlg.put(player.getUniqueId(), now);
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
