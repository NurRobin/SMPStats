package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.timeline.DeathReplayService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MomentListenerTest {

    @Test
    void routesEventsToMomentServiceAndDeathReplay() {
        MomentService momentService = mock(MomentService.class);
        DeathReplayService deathReplay = mock(DeathReplayService.class);
        MomentListener listener = new MomentListener(momentService, deathReplay);

        Player player = mock(Player.class);
        Location loc = mock(Location.class);
        when(player.getLocation()).thenReturn(loc);

        BlockBreakEvent blockBreak = mock(BlockBreakEvent.class);
        when(blockBreak.getPlayer()).thenReturn(player);
        when(blockBreak.getBlock()).thenReturn(mock(org.bukkit.block.Block.class));
        when(blockBreak.getBlock().getLocation()).thenReturn(loc);
        when(blockBreak.getBlock().getType()).thenReturn(Material.DIAMOND_ORE);
        listener.onBlockBreak(blockBreak);
        verify(momentService).onBlockBreak(player, loc, Material.DIAMOND_ORE);

        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        EntityDamageEvent damageEvent = mock(EntityDamageEvent.class);
        when(damageEvent.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(death.getEntity().getLastDamageCause()).thenReturn(damageEvent);
        when(death.getEntity().getFallDistance()).thenReturn(10f);
        listener.onDeath(death);
        verify(momentService).onDeath(player, loc, 10f, "FALL", false);
        verify(deathReplay).capture(player, "FALL", 10f);

        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(player);
        when(damage.getFinalDamage()).thenReturn(1.0);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.LAVA);
        listener.onDamage(damage);
        verify(momentService).onDamage(player, 1.0, "LAVA");

        // Water bucket + zero fall damage triggers MLG moment
        PlayerBucketEmptyEvent bucket = mock(PlayerBucketEmptyEvent.class);
        when(bucket.getBucket()).thenReturn(Material.WATER_BUCKET);
        when(bucket.getPlayer()).thenReturn(player);
        listener.onBucketPlace(bucket);
        EntityDamageEvent fallDamage = mock(EntityDamageEvent.class);
        when(fallDamage.getEntity()).thenReturn(player);
        when(fallDamage.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(fallDamage.getFinalDamage()).thenReturn(0.0);
        when(player.getLocation()).thenReturn(loc);
        listener.onDamage(fallDamage);
        verify(momentService).onBossKill(player, "MLG_WATER", loc);

        EntityPickupItemEvent pickup = mock(EntityPickupItemEvent.class);
        when(pickup.getEntity()).thenReturn(player);
        var item = mock(org.bukkit.entity.Item.class);
        when(item.getItemStack()).thenReturn(mock(org.bukkit.inventory.ItemStack.class));
        when(item.getItemStack().getType()).thenReturn(Material.NETHERITE_INGOT);
        when(item.getLocation()).thenReturn(loc);
        when(pickup.getItem()).thenReturn(item);
        listener.onItemPickup(pickup);
        verify(momentService).onItemGain(player, Material.NETHERITE_INGOT, loc);
    }
}
