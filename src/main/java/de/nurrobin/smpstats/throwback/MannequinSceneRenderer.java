package de.nurrobin.smpstats.throwback;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Figures as Mannequin entities wearing the viewer's skin, props as block and item displays. Every entity is
 * created invisible-by-default inside the hall world and shown to the owner only, which keeps other players
 * from ever receiving it (and avoids the visibility loss of moving entities between worlds, Paper #12599).
 * Nothing is persistent, so a crash leaves nothing in the world files.
 */
public final class MannequinSceneRenderer implements SceneRenderer {
    public static final String ACTION_KEY = "throwback_action";
    public static final String OWNER_KEY = "throwback_owner";
    private static final long SWING_PERIOD_TICKS = 16L;
    private static final double SWING_RANGE_SQUARED = 24.0 * 24.0;

    /** How entities are hidden and shown; replaced in tests because MockBukkit has no per-player visibility. */
    interface Visibility {
        void hideByDefault(Entity entity);

        void show(Player viewer, Plugin plugin, Entity entity);

        default void skin(Mannequin figure, Player viewer) {
            figure.setProfile(ResolvableProfile.resolvableProfile(viewer.getPlayerProfile()));
        }

        default void styleText(TextDisplay display, Component text) {
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            display.setLineWidth(260);
            display.setShadowed(true);
            display.setViewRange(0.5f);
        }

        default void styleInteraction(Interaction interaction, float width, float height) {
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setResponsive(true);
        }
    }

    private static final Visibility PAPER = new Visibility() {
        @Override
        public void hideByDefault(Entity entity) {
            entity.setVisibleByDefault(false);
        }

        @Override
        public void show(Player viewer, Plugin plugin, Entity entity) {
            viewer.showEntity(plugin, entity);
        }
    };

    private final Plugin plugin;
    private final Visibility visibility;
    private final NamespacedKey tag;
    private final NamespacedKey action;
    private final NamespacedKey owner;

    public MannequinSceneRenderer(Plugin plugin) {
        this(plugin, PAPER);
    }

    MannequinSceneRenderer(Plugin plugin, Visibility visibility) {
        this.plugin = plugin;
        this.visibility = visibility;
        this.tag = HallWorld.tagKey(plugin);
        this.action = new NamespacedKey(plugin, ACTION_KEY);
        this.owner = new NamespacedKey(plugin, OWNER_KEY);
    }

    @Override
    public SceneSession open(Player viewer) {
        return new Session(viewer);
    }

    public NamespacedKey actionKey() {
        return action;
    }

    public NamespacedKey ownerKey() {
        return owner;
    }

    private record Swinger(Mannequin figure, Location hit, Material block) {
    }

    private final class Session implements SceneSession {
        private final Player viewer;
        private final List<Entity> entities = new ArrayList<>();
        private final List<Swinger> swingers = new ArrayList<>();
        private BukkitTask swingTask;
        private boolean closed;

        Session(Player viewer) {
            this.viewer = viewer;
        }

        private <T extends Entity> T spawn(Location at, Class<T> type, Consumer<T> setup) {
            World world = at.getWorld();
            T entity = world.spawn(at, type, e -> {
                visibility.hideByDefault(e);
                e.setPersistent(false);
                e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
                e.getPersistentDataContainer().set(owner, PersistentDataType.STRING, viewer.getUniqueId().toString());
                setup.accept(e);
            });
            entities.add(entity);
            visibility.show(viewer, plugin, entity);
            return entity;
        }

        @Override
        public void figure(Location base, SceneSpec spec) {
            if (closed) {
                return;
            }
            float yaw = (float) (base.getYaw() + spec.turn());
            double rad = Math.toRadians(base.getYaw() + spec.turn());
            double fx = -Math.sin(rad);
            double fz = Math.cos(rad);
            double rx = -Math.cos(rad);
            double rz = -Math.sin(rad);
            Location floor = base.clone();
            if (spec.pose() != null) {
                Location at = base.clone().add(0, spec.lift(), 0);
                at.setYaw(yaw);
                at.setPitch(0);
                Mannequin figure = spawn(at, Mannequin.class, m -> setupFigure(m, spec));
                if (spec.animated()) {
                    Location hit = at.clone().add(fx * 0.8 + rx * 0.2, 1.0, fz * 0.8 + rz * 0.2);
                    swingers.add(new Swinger(figure, hit, spec.swingBlock()));
                    startSwinging();
                }
            }
            for (SceneSpec.Prop prop : spec.props()) {
                Location at = floor.clone().add(rx * prop.right() + fx * prop.forward(), prop.up(),
                        rz * prop.right() + fz * prop.forward());
                at.setYaw(yaw);
                spawnProp(at, prop);
            }
        }

        private void setupFigure(Mannequin m, SceneSpec spec) {
            visibility.skin(m, viewer);
            m.setImmovable(true);
            m.setAI(false);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setCustomNameVisible(false);
            m.setDescription(Component.empty());
            if (Mannequin.validPoses().contains(spec.pose())) {
                m.setPose(spec.pose(), true);
            }
            var eq = m.getEquipment();
            if (spec.mainHand() != null) {
                eq.setItemInMainHand(new ItemStack(spec.mainHand()));
            }
            if (spec.offHand() != null) {
                eq.setItemInOffHand(new ItemStack(spec.offHand()));
            }
            if (spec.helmet() != null) {
                eq.setHelmet(new ItemStack(spec.helmet()));
            }
            if (spec.chestplate() != null) {
                eq.setChestplate(new ItemStack(spec.chestplate()));
            }
        }

        private void spawnProp(Location at, SceneSpec.Prop prop) {
            Transformation scale = new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(prop.scale()), new Quaternionf());
            if (prop.block()) {
                Location corner = at.clone().add(-prop.scale() / 2.0, 0, -prop.scale() / 2.0);
                spawn(corner, BlockDisplay.class, b -> {
                    b.setBlock(prop.material().createBlockData());
                    b.setTransformation(scale);
                });
            } else {
                Location center = at.clone().add(0, prop.scale() / 2.0, 0);
                spawn(center, ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(prop.material()));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                    d.setTransformation(scale);
                });
            }
        }

        @Override
        public void text(Location at, Component text) {
            if (closed) {
                return;
            }
            spawn(at, TextDisplay.class, t -> visibility.styleText(t, text));
        }

        @Override
        public void interaction(Location at, float width, float height, String actionName) {
            if (closed) {
                return;
            }
            spawn(at, Interaction.class, i -> {
                visibility.styleInteraction(i, width, height);
                i.getPersistentDataContainer().set(action, PersistentDataType.STRING, actionName);
            });
        }

        private void startSwinging() {
            if (swingTask != null) {
                return;
            }
            swingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::swing, SWING_PERIOD_TICKS, SWING_PERIOD_TICKS);
        }

        private void swing() {
            if (closed || !viewer.isOnline()) {
                return;
            }
            Location eye = viewer.getLocation();
            for (Swinger s : swingers) {
                if (!s.figure().isValid() || s.hit().getWorld() != eye.getWorld()
                        || s.hit().distanceSquared(eye) > SWING_RANGE_SQUARED) {
                    continue;
                }
                s.figure().swingMainHand();
                viewer.spawnParticle(Particle.BLOCK, s.hit(), 8, 0.15, 0.15, 0.15, s.block().createBlockData());
                viewer.playSound(s.hit(), Sound.BLOCK_STONE_HIT, 0.4f, 1f);
            }
        }

        @Override
        public int entityCount() {
            return (int) entities.stream().filter(Entity::isValid).count();
        }

        @Override
        public void close() {
            closed = true;
            if (swingTask != null) {
                swingTask.cancel();
                swingTask = null;
            }
            swingers.clear();
            for (Entity e : entities) {
                if (e.isValid()) {
                    e.remove();
                }
            }
            entities.clear();
        }
    }
}
