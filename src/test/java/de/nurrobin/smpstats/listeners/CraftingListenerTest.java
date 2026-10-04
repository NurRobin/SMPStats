package de.nurrobin.smpstats.listeners;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.Server;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CraftingListenerTest {

    @Test
    void shiftClickCountsItemsThatLandedInInventory() {
        ShiftCraft craft = new ShiftCraft(4, 3, 5); // result 4, matrix min 3 -> at most 12
        // Inventory nearly full: only 8 of the 12 possible items fit.
        craft.inventoryHolds(new int[]{60}, new int[]{64, 4});

        craft.listener.onCraft(craft.event);
        verify(craft.stats, never()).addCrafted(any(), anyLong()); // nothing counted before the tick ran

        craft.runScheduledTask();
        verify(craft.stats).addCrafted(craft.uuid, 8);
    }

    @Test
    void shiftClickCapsGainAtTheoreticalMaximum() {
        ShiftCraft craft = new ShiftCraft(2, 3, 5); // at most 6
        // Something else (e.g. a pickup in the same tick) added more of the same item.
        craft.inventoryHolds(new int[]{}, new int[]{20});

        craft.listener.onCraft(craft.event);
        craft.runScheduledTask();

        verify(craft.stats).addCrafted(craft.uuid, 6);
    }

    @Test
    void shiftClickWithFullInventoryCountsNothing() {
        ShiftCraft craft = new ShiftCraft(4, 3, 5);
        craft.inventoryHolds(new int[]{64}, new int[]{64});

        craft.listener.onCraft(craft.event);
        craft.runScheduledTask();

        verify(craft.stats, never()).addCrafted(any(), anyLong());
    }

    /** Shift-click craft fixture with a mocked scheduler and a before/after player inventory. */
    private static final class ShiftCraft {
        final StatsService stats = mock(StatsService.class);
        final CraftItemEvent event = mock(CraftItemEvent.class);
        final UUID uuid = UUID.randomUUID();
        final CraftingListener listener;
        private final ItemStack result = mock(ItemStack.class);
        private final PlayerInventory playerInventory = mock(PlayerInventory.class);
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);

        ShiftCraft(int resultAmount, int... matrixAmounts) {
            SMPStats plugin = mock(SMPStats.class);
            Settings settings = mock(Settings.class);
            when(settings.isTrackCrafting()).thenReturn(true);
            when(plugin.getSettings()).thenReturn(settings);
            Server server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(scheduler);
            listener = new CraftingListener(plugin, stats);

            Recipe recipe = mock(Recipe.class);
            when(result.getAmount()).thenReturn(resultAmount);
            when(recipe.getResult()).thenReturn(result);
            when(event.getRecipe()).thenReturn(recipe);
            when(event.isShiftClick()).thenReturn(true);

            ItemStack[] matrix = new ItemStack[matrixAmounts.length];
            for (int i = 0; i < matrixAmounts.length; i++) {
                matrix[i] = mock(ItemStack.class);
                when(matrix[i].getAmount()).thenReturn(matrixAmounts[i]);
            }
            CraftingInventory inventory = mock(CraftingInventory.class);
            when(inventory.getMatrix()).thenReturn(matrix);
            when(event.getInventory()).thenReturn(inventory);

            HumanEntity crafter = mock(HumanEntity.class);
            when(crafter.getUniqueId()).thenReturn(uuid);
            when(crafter.getInventory()).thenReturn(playerInventory);
            when(event.getWhoClicked()).thenReturn(crafter);
        }

        void inventoryHolds(int[] before, int[] after) {
            ItemStack unrelated = mock(ItemStack.class);
            when(unrelated.getAmount()).thenReturn(64);
            ItemStack[] beforeContents = stacks(before, unrelated);
            ItemStack[] afterContents = stacks(after, unrelated);
            when(playerInventory.getStorageContents()).thenReturn(beforeContents, afterContents);
        }

        void runScheduledTask() {
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(any(org.bukkit.plugin.Plugin.class), task.capture());
            task.getValue().run();
        }

        private ItemStack[] stacks(int[] amounts, ItemStack unrelated) {
            ItemStack[] contents = new ItemStack[amounts.length + 2];
            for (int i = 0; i < amounts.length; i++) {
                ItemStack stack = mock(ItemStack.class);
                when(stack.getAmount()).thenReturn(amounts[i]);
                when(stack.isSimilar(result)).thenReturn(true);
                contents[i] = stack;
            }
            contents[amounts.length] = null;
            contents[amounts.length + 1] = unrelated;
            return contents;
        }
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
