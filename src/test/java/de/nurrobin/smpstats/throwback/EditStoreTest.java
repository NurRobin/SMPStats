package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EditStoreTest {
    @TempDir
    Path tempDir;

    private EditStore store;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        store = new EditStore(tempDir.resolve("throwback/edit"), Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static List<ItemStack> slots(int size, ItemStack... items) {
        List<ItemStack> out = new ArrayList<>(Arrays.asList(new ItemStack[size]));
        for (int i = 0; i < items.length; i++) {
            out.set(i * 2, items[i]);
        }
        return out;
    }

    @Test
    void inventoryComesBackSlotForSlotIncludingEmptySlotsAndNamedItems() throws Exception {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(m -> m.displayName(Component.text("Excalibur")));
        EditStore.SavedInventory saved = new EditStore.SavedInventory(
                slots(36, sword, new ItemStack(Material.COBBLESTONE, 64)), slots(4, new ItemStack(Material.IRON_HELMET)),
                new ItemStack(Material.SHIELD));

        store.saveInventory(id, saved);
        EditStore.SavedInventory back = store.loadInventory(id).orElseThrow();

        assertTrue(store.hasInventory(id));
        assertEquals(saved.storage(), back.storage());
        assertEquals(saved.armor(), back.armor());
        assertEquals(saved.offHand(), back.offHand());
        assertEquals(36, back.storage().size());
        assertNull(back.storage().get(1));
        assertEquals(64, back.storage().get(2).getAmount());
    }

    @Test
    void emptyOffHandStaysEmpty() throws Exception {
        store.saveInventory(id, new EditStore.SavedInventory(slots(36), slots(4), null));

        assertNull(store.loadInventory(id).orElseThrow().offHand());
    }

    @Test
    void deleteRemovesTheBackup() throws Exception {
        store.saveInventory(id, new EditStore.SavedInventory(slots(36), slots(4), null));

        store.deleteInventory(id);

        assertFalse(store.hasInventory(id));
        assertTrue(store.loadInventory(id).isEmpty());
    }

    @Test
    void draftRoundTripsWithAllKindsAndKeepsTheOrder() throws Exception {
        EditDraft draft = EditDraft.fromLayout(PlaceholderHall.layout().withPrivacy(new Pos(3.5, 64, 90.5, -90f)));
        draft.place(EditMark.ofWall(new WallDef("bauten-9", 1, 64, 2, BlockFace.WEST, 5, 2, "Mein Bau", "Alex & Sam")));

        store.saveDraft(id, draft);
        EditDraft back = store.loadDraft(id).orElseThrow();

        assertEquals(draft.marks(), back.marks());
        assertEquals("Alex & Sam", back.walls().get(back.walls().size() - 1).wall().builders());
        assertEquals(draft.toLayout(), back.toLayout());
    }

    @Test
    void missingOrBrokenDraftIsNotAnError() throws Exception {
        assertTrue(store.loadDraft(id).isEmpty());
        Path dir = Files.createDirectories(tempDir.resolve("throwback/edit"));
        Files.writeString(dir.resolve(id + ".draft.yml"), "marks: [ {{{");

        assertTrue(store.loadDraft(id).isEmpty());
    }

    @Test
    void aBrokenMarkIsSkippedButTheRestIsKept() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("throwback/edit"));
        Files.writeString(dir.resolve(id + ".draft.yml"), """
                marks:
                  - {kind: START, x: 0.5, y: 64.0, z: 0.5, yaw: 0.0}
                  - {kind: NONSENSE, x: 1, y: 1, z: 1, yaw: 0}
                  - {kind: STAGE, x: 1.5}
                """);

        EditDraft draft = store.loadDraft(id).orElseThrow();

        assertEquals(1, draft.marks().size());
        assertEquals(EditKind.START, draft.marks().get(0).kind());
    }

    @Test
    void deletingTheDraftLeavesNothingBehind() throws Exception {
        store.saveDraft(id, new EditDraft());
        assertTrue(store.hasDraft(id));

        store.deleteDraft(id);

        assertFalse(store.hasDraft(id));
        try (var files = Files.list(tempDir.resolve("throwback/edit"))) {
            assertEquals(0, files.count(), "no temp files either");
        }
    }
}
