package de.nurrobin.smpstats.throwback;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Files of the edit mode under {@code throwback/edit/}: the builder's own inventory (so nothing is lost to a crash)
 * and the unsaved draft (so a restart does not cost the work). Writes go through a temp file and are checked.
 */
final class EditStore {
    /** The whole inventory of a player as it was when the edit mode started. */
    record SavedInventory(List<ItemStack> storage, List<ItemStack> armor, ItemStack offHand) {
    }

    private final Path folder;
    private final Logger logger;

    EditStore(Path folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
    }

    private Path inventoryFile(UUID uuid) {
        return folder.resolve(uuid + ".yml");
    }

    private Path draftFile(UUID uuid) {
        return folder.resolve(uuid + ".draft.yml");
    }

    boolean hasInventory(UUID uuid) {
        return Files.isRegularFile(inventoryFile(uuid));
    }

    boolean hasDraft(UUID uuid) {
        return Files.isRegularFile(draftFile(uuid));
    }

    /** Writes the backup and reads it back; throws if what comes back differs, so the inventory is never cleared on a bad backup. */
    void saveInventory(UUID uuid, SavedInventory inventory) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("storage", new ArrayList<>(inventory.storage()));
        yaml.set("armor", new ArrayList<>(inventory.armor()));
        yaml.set("offhand", inventory.offHand());
        write(inventoryFile(uuid), yaml.saveToString());
        SavedInventory back = loadInventory(uuid).orElseThrow(() -> new IOException("Sicherung nicht lesbar"));
        if (!back.storage().equals(inventory.storage()) || !back.armor().equals(inventory.armor())
                || !java.util.Objects.equals(back.offHand(), inventory.offHand())) {
            Files.deleteIfExists(inventoryFile(uuid));
            throw new IOException("Sicherung stimmt nicht mit dem Inventar überein");
        }
    }

    Optional<SavedInventory> loadInventory(UUID uuid) {
        YamlConfiguration yaml = read(inventoryFile(uuid));
        if (yaml == null) {
            return Optional.empty();
        }
        return Optional.of(new SavedInventory(items(yaml.getList("storage")), items(yaml.getList("armor")),
                yaml.getItemStack("offhand")));
    }

    private static List<ItemStack> items(List<?> raw) {
        List<ItemStack> out = new ArrayList<>();
        if (raw != null) {
            for (Object o : raw) {
                out.add(o instanceof ItemStack stack ? stack : null);
            }
        }
        return out;
    }

    void deleteInventory(UUID uuid) {
        delete(inventoryFile(uuid));
    }

    void saveDraft(UUID uuid, EditDraft draft) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (EditMark m : draft.marks()) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("kind", m.kind().name());
            map.put("x", m.x());
            map.put("y", m.y());
            map.put("z", m.z());
            map.put("yaw", (double) m.yaw());
            if (m.wall() != null) {
                map.put("wall", m.wall().toMap());
            }
            list.add(map);
        }
        yaml.set("marks", list);
        write(draftFile(uuid), yaml.saveToString());
    }

    Optional<EditDraft> loadDraft(UUID uuid) {
        YamlConfiguration yaml = read(draftFile(uuid));
        if (yaml == null) {
            return Optional.empty();
        }
        List<EditMark> marks = new ArrayList<>();
        for (Map<?, ?> m : yaml.getMapList("marks")) {
            try {
                EditKind kind = EditKind.valueOf(String.valueOf(m.get("kind")));
                double x = ((Number) m.get("x")).doubleValue();
                double y = ((Number) m.get("y")).doubleValue();
                double z = ((Number) m.get("z")).doubleValue();
                float yaw = ((Number) m.get("yaw")).floatValue();
                if (kind == EditKind.WALL) {
                    marks.addAll(wallMark(m));
                } else {
                    marks.add(EditMark.of(kind, x, y, z, yaw));
                }
            } catch (RuntimeException e) {
                logger.warning("Throwback: Markierung im Entwurf von " + uuid + " übersprungen (" + e.getMessage() + "): " + m);
            }
        }
        return Optional.of(new EditDraft(marks));
    }

    private List<EditMark> wallMark(Map<?, ?> m) {
        ConfigurationSection holder = new YamlConfiguration().createSection("w");
        holder.set("walls", List.of(m.get("wall")));
        return WallDef.readAll(holder, logger).stream().map(EditMark::ofWall).toList();
    }

    void deleteDraft(UUID uuid) {
        delete(draftFile(uuid));
    }

    private void write(Path file, String content) throws IOException {
        Files.createDirectories(folder);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private YamlConfiguration read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            logger.warning("Throwback: " + file.getFileName() + " nicht lesbar: " + e.getMessage());
            return null;
        }
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warning("Throwback: " + file.getFileName() + " ließ sich nicht löschen: " + e.getMessage());
        }
    }

    /** Pads a slot array to a list; keeps nulls so slot positions survive. */
    static List<ItemStack> list(ItemStack[] slots) {
        return new ArrayList<>(Arrays.asList(slots));
    }
}
