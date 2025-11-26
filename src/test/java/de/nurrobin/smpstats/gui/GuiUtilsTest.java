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
}
