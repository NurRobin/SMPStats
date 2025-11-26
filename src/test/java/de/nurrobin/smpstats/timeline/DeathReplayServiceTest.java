package de.nurrobin.smpstats.timeline;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyDouble;

class DeathReplayServiceTest {

    @Test
    void capturesNearbyDataAndBuffersWithLimit() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = mock(Settings.class);
        when(settings.isDeathReplayEnabled()).thenReturn(true);
        when(settings.isDeathReplayInventoryItems()).thenReturn(false);
        when(settings.getDeathReplayNearbyRadius()).thenReturn(5);
        when(settings.getDeathReplayLimit()).thenReturn(1);

        DeathReplayService service = new DeathReplayService(plugin, storage, settings);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        when(player.getHealth()).thenReturn(10.0);
        World world = mock(World.class);
        Location loc = new Location(world, 1, 64, 1);
        when(player.getLocation()).thenReturn(loc);
        when(player.getWorld()).thenReturn(world);

        Player otherPlayer = mock(Player.class);
        when(otherPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(otherPlayer.getName()).thenReturn("Bea");
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);
        when(world.getNearbyEntities(any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(otherPlayer, mob));

        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
        when(player.getInventory()).thenReturn(inv);

        service.capture(player, "FALL", 3.0);
        service.capture(player, "FALL", 4.0); // should evict oldest because limit=1

        verify(storage, times(2)).saveDeathReplay(any());
        assertEquals(1, service.recent(5).size());
    }

    @Test
    void skipsCaptureWhenDisabled() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = mock(Settings.class);
        when(settings.isDeathReplayEnabled()).thenReturn(false);

        DeathReplayService service = new DeathReplayService(plugin, storage, settings);

        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(mock(Location.class));
        when(player.getWorld()).thenReturn(mock(World.class));

        service.capture(player, "VOID", 0);

        verify(storage, never()).saveDeathReplay(any());
        assertTrue(service.recent(1).isEmpty());
    }

    @Test
    void storesInventoryItemsWhenEnabled() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = mock(Settings.class);
        when(settings.isDeathReplayEnabled()).thenReturn(true);
        when(settings.isDeathReplayInventoryItems()).thenReturn(true);
        when(settings.getDeathReplayNearbyRadius()).thenReturn(1);
        when(settings.getDeathReplayLimit()).thenReturn(5);

        DeathReplayService service = new DeathReplayService(plugin, storage, settings);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        when(player.getHealth()).thenReturn(5.0);

        World world = mock(World.class);
        when(world.getNearbyEntities(any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        Location loc = new Location(world, 5, 64, -5);
        when(player.getLocation()).thenReturn(loc);
        when(player.getWorld()).thenReturn(world);

        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack diamond = mock(ItemStack.class);
        when(diamond.getType()).thenReturn(org.bukkit.Material.DIAMOND);
        when(diamond.getAmount()).thenReturn(2);
        ItemStack beef = mock(ItemStack.class);
        when(beef.getType()).thenReturn(org.bukkit.Material.COOKED_BEEF);
        when(beef.getAmount()).thenReturn(3);
        when(inv.getContents()).thenReturn(new ItemStack[]{
                diamond,
                null,
                beef
        });
        when(player.getInventory()).thenReturn(inv);

        service.capture(player, "VOID", 2.5);

        ArgumentCaptor<DeathReplayEntry> captor = ArgumentCaptor.forClass(DeathReplayEntry.class);
        verify(storage).saveDeathReplay(captor.capture());

        List<String> inventory = captor.getValue().inventory();
        assertTrue(inventory.contains("DIAMONDx2"));
        assertTrue(inventory.contains("COOKED_BEEFx3"));
    }
}
