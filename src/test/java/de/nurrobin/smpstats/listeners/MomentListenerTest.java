package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.timeline.DeathReplayService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
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

        EntityPickupItemEvent pickup = pickup(player, Material.NETHERITE_INGOT, 1, 0, null, loc);
        listener.onItemPickup(pickup);
        verify(momentService).onItemGain(player, Material.NETHERITE_INGOT, loc, 1);
    }

    @Test
    void itemPickupCountsPickedUpStackAmount() {
        MomentService momentService = mock(MomentService.class);
        MomentListener listener = new MomentListener(momentService, null);
        Player player = player();
        Location loc = mock(Location.class);

        // 16 diamonds on the ground, 4 did not fit into the inventory
        listener.onItemPickup(pickup(player, Material.DIAMOND, 16, 4, UUID.randomUUID(), loc));

        verify(momentService).onItemGain(player, Material.DIAMOND, loc, 12);
    }

    @Test
    void itemPickupIgnoresItemsThePlayerDroppedThemselves() {
        MomentService momentService = mock(MomentService.class);
        MomentListener listener = new MomentListener(momentService, null);
        Player player = player();

        listener.onItemPickup(pickup(player, Material.DIAMOND, 5, 0, player.getUniqueId(), mock(Location.class)));

        verify(momentService, never()).onItemGain(any(), any(), any(), anyInt());
        verify(momentService, never()).onItemGain(any(), any(), any());
    }

    @Test
    void mlgWaterTriggersWhenLandingInPlacedWaterAfterLongFall() {
        MomentService momentService = mock(MomentService.class);
        MomentListener listener = new MomentListener(momentService, null);
        Player player = player();
        Location loc = mock(Location.class);
        when(player.getLocation()).thenReturn(loc);

        when(player.getFallDistance()).thenReturn(25f);
        listener.onBucketPlace(waterBucket(player));
        listener.onMove(move(player)); // still falling
        verify(momentService, never()).onBossKill(any(), any(), any());

        when(player.getFallDistance()).thenReturn(0f);
        when(player.isInWater()).thenReturn(true);
        listener.onMove(move(player));
        verify(momentService).onBossKill(player, "MLG_WATER", loc);
        assertFalse(listener.hasPendingMlg(player.getUniqueId()));

        listener.onMove(move(player)); // fires only once per bucket
        verify(momentService, times(1)).onBossKill(any(), any(), any());
    }

    @Test
    void mlgWaterIgnoresShortFallsMissedWaterAndFallDamage() {
        MomentService momentService = mock(MomentService.class);
        MomentListener listener = new MomentListener(momentService, null);
        Player player = player();
        UUID uuid = player.getUniqueId();

        // Short fall: just placing water while walking around
        when(player.getFallDistance()).thenReturn(2f);
        listener.onBucketPlace(waterBucket(player));
        assertFalse(listener.hasPendingMlg(uuid));

        // Long fall, but landed next to the water on solid ground
        when(player.getFallDistance()).thenReturn(25f);
        listener.onBucketPlace(waterBucket(player));
        assertTrue(listener.hasPendingMlg(uuid));
        when(player.getFallDistance()).thenReturn(0f);
        when(player.isInWater()).thenReturn(false);
        listener.onMove(move(player));
        assertFalse(listener.hasPendingMlg(uuid));

        // Long fall that still caused fall damage
        when(player.getFallDistance()).thenReturn(25f);
        listener.onBucketPlace(waterBucket(player));
        EntityDamageEvent fall = mock(EntityDamageEvent.class);
        when(fall.getEntity()).thenReturn(player);
        when(fall.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        when(fall.getFinalDamage()).thenReturn(6.0);
        listener.onDamage(fall);
        assertFalse(listener.hasPendingMlg(uuid));

        // Lava bucket never arms the MLG check
        PlayerBucketEmptyEvent lava = mock(PlayerBucketEmptyEvent.class);
        when(lava.getBucket()).thenReturn(Material.LAVA_BUCKET);
        when(lava.getPlayer()).thenReturn(player);
        listener.onBucketPlace(lava);
        assertFalse(listener.hasPendingMlg(uuid));

        verify(momentService, never()).onBossKill(any(), any(), any());
    }

    @Test
    void mlgWaterExpiresAndIsClearedOnQuitAndDeath() throws Exception {
        MomentService momentService = mock(MomentService.class);
        MomentListener listener = new MomentListener(momentService, null);
        Player player = player();
        UUID uuid = player.getUniqueId();
        when(player.getFallDistance()).thenReturn(25f);

        java.lang.reflect.Field mapField = MomentListener.class.getDeclaredField("pendingMlg");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<UUID, Long> map = (java.util.Map<UUID, Long>) mapField.get(listener);
        map.put(uuid, System.currentTimeMillis() - 5000); // stale
        when(player.getFallDistance()).thenReturn(0f);
        when(player.isInWater()).thenReturn(true);
        listener.onMove(move(player));
        verify(momentService, never()).onBossKill(any(), any(), any());
        assertFalse(listener.hasPendingMlg(uuid));

        when(player.getFallDistance()).thenReturn(25f);
        when(player.isInWater()).thenReturn(false);
        listener.onBucketPlace(waterBucket(player));
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onQuit(quit);
        assertFalse(listener.hasPendingMlg(uuid));

        listener.onBucketPlace(waterBucket(player));
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        listener.onDeath(death);
        assertFalse(listener.hasPendingMlg(uuid));
    }

    private Player player() {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    private PlayerBucketEmptyEvent waterBucket(Player player) {
        PlayerBucketEmptyEvent bucket = mock(PlayerBucketEmptyEvent.class);
        when(bucket.getBucket()).thenReturn(Material.WATER_BUCKET);
        when(bucket.getPlayer()).thenReturn(player);
        return bucket;
    }

    private PlayerMoveEvent move(Player player) {
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private EntityPickupItemEvent pickup(Player player, Material type, int amount, int remaining, UUID thrower, Location loc) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        Item item = mock(Item.class);
        when(item.getItemStack()).thenReturn(stack);
        when(item.getThrower()).thenReturn(thrower);
        when(item.getLocation()).thenReturn(loc);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getItem()).thenReturn(item);
        when(event.getRemaining()).thenReturn(remaining);
        return event;
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
    void ignoresNonPlayerEvents() {
        MomentService momentService = mock(MomentService.class);
        MomentListener listener = new MomentListener(momentService, null);

        EntityDamageEvent nonPlayerDamage = mock(EntityDamageEvent.class);
        when(nonPlayerDamage.getEntity()).thenReturn(mock(Zombie.class));
        listener.onDamage(nonPlayerDamage);
        verify(momentService, never()).onDamage(any(), anyDouble(), any());

        EntityPickupItemEvent pickup = mock(EntityPickupItemEvent.class);
        when(pickup.getEntity()).thenReturn(mock(Zombie.class));
        listener.onItemPickup(pickup);
        verify(momentService, never()).onItemGain(any(), any(), any(), anyInt());
    }
}
