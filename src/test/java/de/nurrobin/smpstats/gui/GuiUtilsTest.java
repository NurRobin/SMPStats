package de.nurrobin.smpstats.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GuiUtilsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createGuiItemWithNoLore() {
        ItemStack item = GuiUtils.createGuiItem(Material.DIAMOND, Component.text("Test", NamedTextColor.GOLD));
        
        assertNotNull(item);
        assertEquals(Material.DIAMOND, item.getType());
        assertEquals(1, item.getAmount());
        
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertEquals(Component.text("Test", NamedTextColor.GOLD), meta.displayName());
        assertTrue(meta.lore() == null || meta.lore().isEmpty());
    }

    @Test
    void createGuiItemWithLore() {
        ItemStack item = GuiUtils.createGuiItem(
                Material.EMERALD, 
                Component.text("Title", NamedTextColor.GREEN),
                Component.text("Line 1", NamedTextColor.GRAY),
                Component.text("Line 2", NamedTextColor.WHITE)
        );
        
        assertNotNull(item);
        assertEquals(Material.EMERALD, item.getType());
        
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertEquals(Component.text("Title", NamedTextColor.GREEN), meta.displayName());
        
        List<Component> lore = meta.lore();
        assertNotNull(lore);
        assertEquals(2, lore.size());
        assertEquals(Component.text("Line 1", NamedTextColor.GRAY), lore.get(0));
        assertEquals(Component.text("Line 2", NamedTextColor.WHITE), lore.get(1));
    }

    @Test
    void createPlayerHead() {
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer("TestPlayer");
        
        ItemStack item = GuiUtils.createPlayerHead(player, 
                Component.text("Player Head", NamedTextColor.GOLD),
                Component.text("Description", NamedTextColor.GRAY));
        
        assertNotNull(item);
        assertEquals(Material.PLAYER_HEAD, item.getType());
        
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        assertNotNull(meta);
        assertEquals(Component.text("Player Head", NamedTextColor.GOLD), meta.displayName());
        assertNotNull(meta.getOwningPlayer());
    }

    @Test
    void createRankedPlayerHead() {
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer("RankedPlayer");
        
        ItemStack item = GuiUtils.createRankedPlayerHead(player, 5, 
                Component.text("Player Name", NamedTextColor.WHITE),
                Component.text("Stat: 100", NamedTextColor.GOLD));
        
        assertNotNull(item);
        assertEquals(Material.PLAYER_HEAD, item.getType());
        assertEquals(5, item.getAmount()); // Rank as item amount
        
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        assertNotNull(meta);
        assertNotNull(meta.getOwningPlayer());
    }

    @Test
    void createRankedPlayerHeadCapsAt64() {
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer("HighRankPlayer");
        
        ItemStack item = GuiUtils.createRankedPlayerHead(player, 100, 
                Component.text("Player Name", NamedTextColor.WHITE));
        
        assertNotNull(item);
        assertEquals(64, item.getAmount()); // Capped at 64
    }

    @Test
    void createRankedPlayerHeadByUuid() {
        UUID uuid = UUID.randomUUID();
        
        ItemStack item = GuiUtils.createRankedPlayerHeadByUuid(uuid, "TestPlayer", 3, 
                "Playtime: 10h", NamedTextColor.GOLD);
        
        assertNotNull(item);
        assertEquals(Material.PLAYER_HEAD, item.getType());
        assertEquals(3, item.getAmount());
    }

    @Test
    void getRankPrefixForTop3() {
        Component rank1 = GuiUtils.getRankPrefix(1);
        Component rank2 = GuiUtils.getRankPrefix(2);
        Component rank3 = GuiUtils.getRankPrefix(3);
        
        assertNotNull(rank1);
        assertNotNull(rank2);
        assertNotNull(rank3);
        
        // Top 3 should have special formatting (contains emoji)
        assertTrue(rank1.toString().contains("1"));
        assertTrue(rank2.toString().contains("2"));
        assertTrue(rank3.toString().contains("3"));
    }

    @Test
    void getRankPrefixForLowerRanks() {
        Component rank10 = GuiUtils.getRankPrefix(10);
        Component rank50 = GuiUtils.getRankPrefix(50);
        
        assertNotNull(rank10);
        assertNotNull(rank50);
    }

    @Test
    void getRankColorForTop3() {
        assertEquals(NamedTextColor.GOLD, GuiUtils.getRankColor(1));
        assertEquals(NamedTextColor.GRAY, GuiUtils.getRankColor(2));
        assertEquals(NamedTextColor.RED, GuiUtils.getRankColor(3));
    }

    @Test
    void getRankColorForLowerRanks() {
        assertEquals(NamedTextColor.WHITE, GuiUtils.getRankColor(4));
        assertEquals(NamedTextColor.WHITE, GuiUtils.getRankColor(10));
        assertEquals(NamedTextColor.WHITE, GuiUtils.getRankColor(50));
    }

    @Test
    void createBorderItem() {
        ItemStack item = GuiUtils.createBorderItem(Material.GRAY_STAINED_GLASS_PANE);
        
        assertNotNull(item);
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, item.getType());
        assertEquals(1, item.getAmount());
    }

    @Test
    void createInfoItem() {
        ItemStack item = GuiUtils.createInfoItem("Info Title", "Line 1", "Line 2", "Line 3");
        
        assertNotNull(item);
        assertEquals(Material.BOOK, item.getType());
        
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertNotNull(meta.displayName());
        
        List<Component> lore = meta.lore();
        assertNotNull(lore);
        assertEquals(3, lore.size());
    }

    @Test
    void soundMethodsDoNotThrow() {
        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer();
        
        assertDoesNotThrow(() -> GuiUtils.playClickSound(player));
        assertDoesNotThrow(() -> GuiUtils.playSuccessSound(player));
        assertDoesNotThrow(() -> GuiUtils.playErrorSound(player));
        assertDoesNotThrow(() -> GuiUtils.playPageTurnSound(player));
    }
}
