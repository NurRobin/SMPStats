package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockListenerTest {

    private ServerMock server;
    private SMPStats plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setsOwnerOnTileStateAndCountsBlocks() {
        // Plugin has tracking enabled by default
        StatsService stats = mock(StatsService.class);
        BlockListener listener = new BlockListener(plugin, stats);
        server.getPluginManager().registerEvents(listener, plugin);

        WorldMock world = server.addSimpleWorld("testworld");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.CHEST);

        BlockPlaceEvent event = new BlockPlaceEvent(
                block, 
                block.getState(), 
                block, 
                new ItemStack(Material.CHEST), 
                player, 
                true, 
                EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(event);

        // Verify blocks placed is counted
        verify(stats).addBlocksPlaced(player.getUniqueId());
        
        // Verify owner is set on tile state
        BlockState state = block.getState();
        if (state instanceof TileState tileState) {
            NamespacedKey ownerKey = new NamespacedKey(plugin, "owner");
            String owner = tileState.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            assertEquals(player.getUniqueId().toString(), owner);
        }
    }

    @Test
    void countsBlocksWhenTrackingEnabled() {
        // The plugin has tracking enabled by default
        StatsService stats = mock(StatsService.class);
        BlockListener listener = new BlockListener(plugin, stats);
        server.getPluginManager().registerEvents(listener, plugin);

        WorldMock world = server.addSimpleWorld("testworld");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.STONE);

        BlockPlaceEvent place = new BlockPlaceEvent(
                block, 
                block.getState(), 
                block, 
                new ItemStack(Material.STONE), 
                player, 
                true, 
                EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(place);
        verify(stats).addBlocksPlaced(player.getUniqueId());

        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(breakEvent);
        verify(stats).addBlocksBroken(player.getUniqueId());
    }

    @Test
    void ignoresBlocksWhenTrackingDisabled() {
        // Disable block tracking via config and save before reload
        plugin.getConfig().set("tracking.blocks", false);
        plugin.saveConfig();
        plugin.reloadPluginConfig(server.getConsoleSender());
        
        // Verify tracking is now disabled
        assertFalse(plugin.getSettings().isTrackBlocks(), "Block tracking should be disabled");
        
        StatsService stats = mock(StatsService.class);
        BlockListener listener = new BlockListener(plugin, stats);
        server.getPluginManager().registerEvents(listener, plugin);
        
        WorldMock world = server.addSimpleWorld("disabledworld");
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.STONE);
        
        // Place block - should be ignored
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                block,
                block.getState(),
                block,
                new ItemStack(Material.STONE),
                player,
                true,
                EquipmentSlot.HAND
        );
        server.getPluginManager().callEvent(placeEvent);
        verify(stats, never()).addBlocksPlaced(org.mockito.ArgumentMatchers.any());
        
        // Break block - should be ignored
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(breakEvent);
        verify(stats, never()).addBlocksBroken(org.mockito.ArgumentMatchers.any());
        
        // Re-enable tracking for other tests
        plugin.getConfig().set("tracking.blocks", true);
        plugin.saveConfig();
        plugin.reloadPluginConfig(server.getConsoleSender());
    }
}
