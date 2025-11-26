package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CraftingListenerTest {

    @Test
    void countsCraftedItemsWithShiftClick() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackCrafting()).thenReturn(true);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        CraftingListener listener = new CraftingListener(plugin, stats);

        CraftItemEvent event = mock(CraftItemEvent.class);
        Recipe recipe = mock(Recipe.class);
        when(event.getRecipe()).thenReturn(recipe);
        when(event.isShiftClick()).thenReturn(true);
        ItemStack result = mock(ItemStack.class);
        when(result.getAmount()).thenReturn(2);
        when(recipe.getResult()).thenReturn(result);

        ItemStack first = mock(ItemStack.class);
        when(first.getAmount()).thenReturn(3);
        ItemStack second = mock(ItemStack.class);
        when(second.getAmount()).thenReturn(5);
        CraftingInventory inventory = mock(CraftingInventory.class);
        when(inventory.getMatrix()).thenReturn(new ItemStack[]{first, second});
        when(event.getInventory()).thenReturn(inventory);

        HumanEntity crafter = mock(HumanEntity.class);
        UUID uuid = UUID.randomUUID();
        when(crafter.getUniqueId()).thenReturn(uuid);
        when(event.getWhoClicked()).thenReturn(crafter);

        listener.onCraft(event);

        // min(stack amounts)=3, result amount=2 -> 6 total crafted
        verify(stats).addCrafted(uuid, 6);
    }

    @Test
    void ignoresCraftingAndConsumptionWhenDisabled() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackCrafting()).thenReturn(false);
        when(settings.isTrackConsumption()).thenReturn(false);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        CraftingListener listener = new CraftingListener(plugin, stats);

        CraftItemEvent craft = mock(CraftItemEvent.class);
        listener.onCraft(craft);
        verify(stats, never()).addCrafted(any(), anyLong());

        PlayerItemConsumeEvent consume = mock(PlayerItemConsumeEvent.class);
        listener.onConsume(consume);
        verify(stats, never()).addConsumed(any());
    }

    @Test
    void countsConsumptionWhenEnabled() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackConsumption()).thenReturn(true);
        when(settings.isTrackCrafting()).thenReturn(false);
        when(plugin.getSettings()).thenReturn(settings);

        StatsService stats = mock(StatsService.class);
        CraftingListener listener = new CraftingListener(plugin, stats);

        PlayerItemConsumeEvent consume = mock(PlayerItemConsumeEvent.class);
        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(consume.getPlayer()).thenReturn(player);

        listener.onConsume(consume);
        verify(stats).addConsumed(uuid);
    }

    @Test
    void ignoresCraftWhenNoResultOrNoInputs() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackCrafting()).thenReturn(true);
        when(plugin.getSettings()).thenReturn(settings);
        StatsService stats = mock(StatsService.class);
        CraftingListener listener = new CraftingListener(plugin, stats);

        // Missing result -> ignore
        CraftItemEvent noResult = mock(CraftItemEvent.class);
        Recipe recipe = mock(Recipe.class);
        when(recipe.getResult()).thenReturn(null);
        when(noResult.getRecipe()).thenReturn(recipe);
        listener.onCraft(noResult);
        verify(stats, never()).addCrafted(any(), anyLong());

        // Shift-click with empty matrix -> calculated crafts = 0 -> ignore
        CraftItemEvent emptyMatrix = mock(CraftItemEvent.class);
        Recipe recipe2 = mock(Recipe.class);
        ItemStack result = mock(ItemStack.class);
        when(result.getAmount()).thenReturn(1);
        when(recipe2.getResult()).thenReturn(result);
        when(emptyMatrix.getRecipe()).thenReturn(recipe2);
        when(emptyMatrix.isShiftClick()).thenReturn(true);
        CraftingInventory inventory = mock(CraftingInventory.class);
        when(inventory.getMatrix()).thenReturn(new ItemStack[]{null, null});
        when(emptyMatrix.getInventory()).thenReturn(inventory);
        listener.onCraft(emptyMatrix);
        verify(stats, never()).addCrafted(any(), anyLong());
    }

    @Test
    void countsRegularCraftWithoutShift() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isTrackCrafting()).thenReturn(true);
        when(plugin.getSettings()).thenReturn(settings);
        StatsService stats = mock(StatsService.class);
        CraftingListener listener = new CraftingListener(plugin, stats);

        CraftItemEvent event = mock(CraftItemEvent.class);
        Recipe recipe = mock(Recipe.class);
        ItemStack result = mock(ItemStack.class);
        when(result.getAmount()).thenReturn(4);
        when(recipe.getResult()).thenReturn(result);
        when(event.getRecipe()).thenReturn(recipe);
        when(event.isShiftClick()).thenReturn(false);

        HumanEntity crafter = mock(HumanEntity.class);
        UUID uuid = UUID.randomUUID();
        when(crafter.getUniqueId()).thenReturn(uuid);
        when(event.getWhoClicked()).thenReturn(crafter);

        listener.onCraft(event);
        verify(stats).addCrafted(uuid, 4);
    }
}
