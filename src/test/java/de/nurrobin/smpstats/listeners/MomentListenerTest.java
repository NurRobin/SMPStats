package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.timeline.DeathReplayService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
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

    @Test
    void detectsSelfInflictedExplosionOnDeath() {
        MomentService momentService = mock(MomentService.class);
        MomentListener listener = new MomentListener(momentService, null);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Location loc = mock(Location.class);
        when(player.getLocation()).thenReturn(loc);
        when(player.getFallDistance()).thenReturn(0f);

        TNTPrimed tnt = mock(TNTPrimed.class);
        when(tnt.getSource()).thenReturn(player);

        EntityDamageByEntityEvent damage = mock(EntityDamageByEntityEvent.class);
        when(damage.getDamager()).thenReturn(tnt);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);

        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        when(death.getEntity().getLastDamageCause()).thenReturn(damage);

        listener.onDeath(death);

        verify(momentService).onDeath(player, loc, 0f, "ENTITY_EXPLOSION", true);
    }

    @Test
    void handlesBossDeathEvent() {
        MomentService momentService = mock(MomentService.class);
        MomentListener listener = new MomentListener(momentService, null);

        Player killer = mock(Player.class);
        Location loc = mock(Location.class);

        LivingEntity wither = mock(LivingEntity.class);
        when(wither.getKiller()).thenReturn(killer);
        when(wither.getLocation()).thenReturn(loc);

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntityType()).thenReturn(EntityType.WITHER);
        when(event.getEntity()).thenReturn(wither);

        listener.onBossDeath(event);

        verify(momentService).onBossKill(killer, "WITHER", loc);
    }

    @Test
    void ignoresNonPlayerEventsAndStaleMlg() throws Exception {
        MomentService momentService = mock(MomentService.class);
        MomentListener listener = new MomentListener(momentService, null);

        EntityDamageEvent nonPlayerDamage = mock(EntityDamageEvent.class);
        when(nonPlayerDamage.getEntity()).thenReturn(mock(Zombie.class));
        listener.onDamage(nonPlayerDamage);
        verify(momentService, never()).onDamage(any(), anyDouble(), any());

        EntityPickupItemEvent pickup = mock(EntityPickupItemEvent.class);
        when(pickup.getEntity()).thenReturn(mock(Zombie.class));
        listener.onItemPickup(pickup);
        verify(momentService, never()).onItemGain(any(), any(), any());

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        Location loc = mock(Location.class);
        when(player.getLocation()).thenReturn(loc);

        java.lang.reflect.Field mapField = MomentListener.class.getDeclaredField("lastWaterPlace");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<UUID, Long> map = (java.util.Map<UUID, Long>) mapField.get(listener);
        map.put(uuid, System.currentTimeMillis() - 5000); // stale

        EntityDamageEvent fall = mock(EntityDamageEvent.class);
        when(fall.getEntity()).thenReturn(player);
        when(fall.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(fall.getFinalDamage()).thenReturn(0.0);
        listener.onDamage(fall);
        verify(momentService, never()).onBossKill(any(), any(), any());

        PlayerBucketEmptyEvent lava = mock(PlayerBucketEmptyEvent.class);
        when(lava.getBucket()).thenReturn(Material.LAVA_BUCKET);
        listener.onBucketPlace(lava);
    }
}
