package de.nurrobin.smpstats.throwback;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * "Bauten 2025": PNGs from the images folder as map art in invisible item frames, identical for everyone.
 * Map ids are stored in {@code mapwall.yml} so a restart reuses the same maps instead of creating new ones;
 * a wall without a PNG stays empty.
 */
public final class MapArtWall {
    private static final int TILE = 128;
    private static final int MAX_IMAGE_SIDE = 4096;

    private final Plugin plugin;
    private final Logger logger;
    private final NamespacedKey wallTag;
    private final Path idFile;

    public MapArtWall(Plugin plugin, Path idFile) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.wallTag = new NamespacedKey(plugin, "throwback_wall");
        this.idFile = idFile;
    }

    /** Rebuilds all walls in the (loaded) hall world; returns the number of walls that got a picture. */
    public int build(World world, List<WallDef> walls, Path imagesDir) {
        YamlConfiguration ids = readIds();
                int shown = 0;
        for (WallDef wall : walls) {
            clearWall(world, wall);
            Path png = imagesDir.resolve(wall.name() + ".png").normalize();
            if (!png.startsWith(imagesDir.normalize()) || !Files.isRegularFile(png)) {
                logger.info("Throwback: kein Bild für die Wand '" + wall.name() + "' (erwartet: " + png + "), sie bleibt leer");
                spawnCaption(world, wall);
                continue;
            }
            BufferedImage image = readScaled(png, wall.width() * TILE, wall.height() * TILE);
            if (image == null) {
                spawnCaption(world, wall);
                continue;
            }
            placeTiles(world, wall, image, ids);
            spawnCaption(world, wall);
            shown++;
        }
        saveIds(ids);
        return shown;
    }

    /** Takes down the frames and captions of walls that are no longer part of the layout. */
    public void clear(World world, List<WallDef> walls) {
        walls.forEach(w -> clearWall(world, w));
    }

    private void placeTiles(World world, WallDef wall, BufferedImage image, YamlConfiguration ids) {
        BlockFace right = wall.right();
        for (int ty = 0; ty < wall.height(); ty++) {
            for (int tx = 0; tx < wall.width(); tx++) {
                String key = wall.name() + "." + tx + "-" + ty;
                MapView view = view(world, ids, key);
                view.getRenderers().forEach(view::removeRenderer);
                view.addRenderer(new TileRenderer(image.getSubimage(tx * TILE, ty * TILE, TILE, TILE)));
                view.setTrackingPosition(false);
                view.setUnlimitedTracking(false);
                view.setLocked(true);
                ItemStack map = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) map.getItemMeta();
                meta.setMapView(view);
                map.setItemMeta(meta);
                int x = wall.x() + right.getModX() * tx;
                int y = wall.y() + (wall.height() - 1 - ty);
                int z = wall.z() + right.getModZ() * tx;
                Location at = new Location(world, x + 0.5, y + 0.5, z + 0.5);
                world.spawn(at, ItemFrame.class, frame -> {
                    frame.setFacingDirection(wall.facing(), true);
                    frame.setVisible(false);
                    frame.setFixed(true);
                    frame.setItem(map, false);
                    frame.setItemDropChance(0f);
                    frame.getPersistentDataContainer().set(wallTag, PersistentDataType.STRING, wall.name());
                });
            }
        }
    }

    private MapView view(World world, YamlConfiguration ids, String key) {
        int id = ids.getInt(key, -1);
        MapView view = id >= 0 ? Bukkit.getMap(id) : null;
        if (view == null) {
            view = Bukkit.createMap(world);
            ids.set(key, view.getId());
        }
        return view;
    }

    private YamlConfiguration readIds() {
        YamlConfiguration cfg = new YamlConfiguration();
        if (Files.isRegularFile(idFile)) {
            try {
                cfg.loadFromString(Files.readString(idFile));
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                logger.warning("Throwback: mapwall.yml nicht lesbar, es werden neue Karten angelegt: " + e.getMessage());
            }
        }
        return cfg;
    }

    private void saveIds(YamlConfiguration ids) {
        try {
            Files.createDirectories(idFile.getParent());
            Files.writeString(idFile, ids.saveToString());
        } catch (IOException e) {
            logger.warning("Throwback: mapwall.yml nicht gespeichert: " + e.getMessage());
        }
    }

    private BufferedImage readScaled(Path png, int width, int height) {
        try {
            BufferedImage src = ImageIO.read(png.toFile());
            if (src == null) {
                logger.warning("Throwback: " + png.getFileName() + " ist kein lesbares Bild");
                return null;
            }
            if (src.getWidth() > MAX_IMAGE_SIDE || src.getHeight() > MAX_IMAGE_SIDE) {
                logger.warning("Throwback: " + png.getFileName() + " ist zu groß (max. " + MAX_IMAGE_SIDE + " Pixel je Seite)");
                return null;
            }
            BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, width, height, null);
            g.dispose();
            return out;
        } catch (IOException | RuntimeException e) {
            logger.warning("Throwback: " + png.getFileName() + " konnte nicht gelesen werden: " + e.getMessage());
            return null;
        }
    }

    private void spawnCaption(World world, WallDef wall) {
        BlockFace right = wall.right();
        double mid = (wall.width() - 1) / 2.0;
        double x = wall.x() + 0.5 + right.getModX() * mid + wall.facing().getModX() * 0.45;
        double z = wall.z() + 0.5 + right.getModZ() * mid + wall.facing().getModZ() * 0.45;
        double y = wall.y() + wall.height() + 0.3;
        Component text = Component.text(wall.title());
        if (!wall.builders().isBlank()) {
            text = text.appendNewline().append(ThrowbackTexts.render("<gray>Erbaut von " + escape(wall.builders())));
        }
        Component finalText = text;
        world.spawn(new Location(world, x, y, z), TextDisplay.class, t -> {
            t.text(finalText);
            t.setBillboard(Display.Billboard.VERTICAL);
            t.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            t.setViewRange(0.5f);
            t.getPersistentDataContainer().set(wallTag, PersistentDataType.STRING, wall.name());
        });
    }

    private static String escape(String s) {
        return s.replace("<", "\\<");
    }

    /** Removes frames and captions of one wall, including those saved in the world from an earlier run. */
    private void clearWall(World world, WallDef wall) {
        BlockFace right = wall.right();
        int x2 = wall.x() + right.getModX() * (wall.width() - 1);
        int z2 = wall.z() + right.getModZ() * (wall.width() - 1);
        for (int cx = Math.min(wall.x(), x2) >> 4; cx <= Math.max(wall.x(), x2) >> 4; cx++) {
            for (int cz = Math.min(wall.z(), z2) >> 4; cz <= Math.max(wall.z(), z2) >> 4; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                for (Entity e : chunk.getEntities()) {
                    if (wall.name().equals(e.getPersistentDataContainer().get(wallTag, PersistentDataType.STRING))) {
                        e.remove();
                    }
                }
            }
        }
    }

    private static final class TileRenderer extends MapRenderer {
        private final BufferedImage tile;
        private boolean drawn;

        TileRenderer(BufferedImage tile) {
            super(false);
            this.tile = tile;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (!drawn) {
                canvas.drawImage(0, 0, tile);
                drawn = true;
            }
        }
    }
}
