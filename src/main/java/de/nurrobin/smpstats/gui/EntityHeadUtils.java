package de.nurrobin.smpstats.gui;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Utility class for mapping entity types to appropriate display materials (heads/items).
 */
public final class EntityHeadUtils {
    
    private static final Map<EntityType, Material> ENTITY_HEADS = new EnumMap<>(EntityType.class);
    
    static {
        // Mob heads
        ENTITY_HEADS.put(EntityType.ZOMBIE, Material.ZOMBIE_HEAD);
        ENTITY_HEADS.put(EntityType.SKELETON, Material.SKELETON_SKULL);
        ENTITY_HEADS.put(EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL);
        ENTITY_HEADS.put(EntityType.CREEPER, Material.CREEPER_HEAD);
        ENTITY_HEADS.put(EntityType.ENDER_DRAGON, Material.DRAGON_HEAD);
        ENTITY_HEADS.put(EntityType.PIGLIN, Material.PIGLIN_HEAD);
        
        // Animals - use spawn eggs or representative items
        ENTITY_HEADS.put(EntityType.PIG, Material.PIG_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.COW, Material.COW_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.SHEEP, Material.SHEEP_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.CHICKEN, Material.CHICKEN_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.HORSE, Material.HORSE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.DONKEY, Material.DONKEY_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.MULE, Material.MULE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.WOLF, Material.WOLF_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.CAT, Material.CAT_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.OCELOT, Material.OCELOT_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.RABBIT, Material.RABBIT_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.PARROT, Material.PARROT_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.TURTLE, Material.TURTLE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.DOLPHIN, Material.DOLPHIN_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.COD, Material.COD_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.SALMON, Material.SALMON_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.PUFFERFISH, Material.PUFFERFISH_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.TROPICAL_FISH, Material.TROPICAL_FISH_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.SQUID, Material.SQUID_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.GLOW_SQUID, Material.GLOW_SQUID_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.AXOLOTL, Material.AXOLOTL_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.GOAT, Material.GOAT_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.FOX, Material.FOX_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.PANDA, Material.PANDA_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.POLAR_BEAR, Material.POLAR_BEAR_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.BEE, Material.BEE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.LLAMA, Material.LLAMA_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.TRADER_LLAMA, Material.TRADER_LLAMA_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.MOOSHROOM, Material.MOOSHROOM_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.FROG, Material.FROG_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.TADPOLE, Material.TADPOLE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.ALLAY, Material.ALLAY_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.CAMEL, Material.CAMEL_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.SNIFFER, Material.SNIFFER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.ARMADILLO, Material.ARMADILLO_SPAWN_EGG);
        
        // Hostile mobs
        ENTITY_HEADS.put(EntityType.SPIDER, Material.SPIDER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.CAVE_SPIDER, Material.CAVE_SPIDER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.ENDERMAN, Material.ENDERMAN_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.SLIME, Material.SLIME_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.MAGMA_CUBE, Material.MAGMA_CUBE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.BLAZE, Material.BLAZE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.GHAST, Material.GHAST_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.ZOMBIE_VILLAGER, Material.ZOMBIE_VILLAGER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.DROWNED, Material.DROWNED_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.HUSK, Material.HUSK_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.STRAY, Material.STRAY_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.PHANTOM, Material.PHANTOM_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.WITCH, Material.WITCH_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.PILLAGER, Material.PILLAGER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.VINDICATOR, Material.VINDICATOR_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.EVOKER, Material.EVOKER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.RAVAGER, Material.RAVAGER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.VEX, Material.VEX_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.GUARDIAN, Material.GUARDIAN_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.ELDER_GUARDIAN, Material.ELDER_GUARDIAN_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.SHULKER, Material.SHULKER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.ENDERMITE, Material.ENDERMITE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.SILVERFISH, Material.SILVERFISH_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.WITHER, Material.WITHER_SKELETON_SKULL);
        ENTITY_HEADS.put(EntityType.HOGLIN, Material.HOGLIN_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.ZOGLIN, Material.ZOGLIN_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.PIGLIN_BRUTE, Material.PIGLIN_BRUTE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.STRIDER, Material.STRIDER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.WARDEN, Material.WARDEN_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.BREEZE, Material.BREEZE_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.BOGGED, Material.BOGGED_SPAWN_EGG);
        
        // Neutral/NPCs
        ENTITY_HEADS.put(EntityType.VILLAGER, Material.VILLAGER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.WANDERING_TRADER, Material.WANDERING_TRADER_SPAWN_EGG);
        ENTITY_HEADS.put(EntityType.IRON_GOLEM, Material.IRON_BLOCK);
        ENTITY_HEADS.put(EntityType.SNOW_GOLEM, Material.CARVED_PUMPKIN);
        ENTITY_HEADS.put(EntityType.ZOMBIFIED_PIGLIN, Material.ZOMBIFIED_PIGLIN_SPAWN_EGG);
        
        // Vehicles/Objects
        ENTITY_HEADS.put(EntityType.MINECART, Material.MINECART);
        ENTITY_HEADS.put(EntityType.CHEST_MINECART, Material.CHEST_MINECART);
        ENTITY_HEADS.put(EntityType.FURNACE_MINECART, Material.FURNACE_MINECART);
        ENTITY_HEADS.put(EntityType.TNT_MINECART, Material.TNT_MINECART);
        ENTITY_HEADS.put(EntityType.HOPPER_MINECART, Material.HOPPER_MINECART);
        ENTITY_HEADS.put(EntityType.SPAWNER_MINECART, Material.MINECART);
        ENTITY_HEADS.put(EntityType.COMMAND_BLOCK_MINECART, Material.COMMAND_BLOCK_MINECART);
        // Boats are individual types in newer versions (OAK_BOAT, etc.)
        
        // Items/Projectiles
        ENTITY_HEADS.put(EntityType.ITEM, Material.CHEST);
        ENTITY_HEADS.put(EntityType.EXPERIENCE_ORB, Material.EXPERIENCE_BOTTLE);
        ENTITY_HEADS.put(EntityType.ARROW, Material.ARROW);
        ENTITY_HEADS.put(EntityType.SPECTRAL_ARROW, Material.SPECTRAL_ARROW);
        ENTITY_HEADS.put(EntityType.TRIDENT, Material.TRIDENT);
        ENTITY_HEADS.put(EntityType.FIREBALL, Material.FIRE_CHARGE);
        ENTITY_HEADS.put(EntityType.SMALL_FIREBALL, Material.FIRE_CHARGE);
        ENTITY_HEADS.put(EntityType.DRAGON_FIREBALL, Material.FIRE_CHARGE);
        ENTITY_HEADS.put(EntityType.WITHER_SKULL, Material.WITHER_SKELETON_SKULL);
        ENTITY_HEADS.put(EntityType.SNOWBALL, Material.SNOWBALL);
        ENTITY_HEADS.put(EntityType.EGG, Material.EGG);
        ENTITY_HEADS.put(EntityType.ENDER_PEARL, Material.ENDER_PEARL);
        ENTITY_HEADS.put(EntityType.EYE_OF_ENDER, Material.ENDER_EYE);
        ENTITY_HEADS.put(EntityType.SPLASH_POTION, Material.SPLASH_POTION);
        ENTITY_HEADS.put(EntityType.FIREWORK_ROCKET, Material.FIREWORK_ROCKET);
        ENTITY_HEADS.put(EntityType.TNT, Material.TNT);
        ENTITY_HEADS.put(EntityType.FALLING_BLOCK, Material.SAND);
        ENTITY_HEADS.put(EntityType.WIND_CHARGE, Material.BREEZE_ROD);
        
        // Display/Decorations
        ENTITY_HEADS.put(EntityType.ARMOR_STAND, Material.ARMOR_STAND);
        ENTITY_HEADS.put(EntityType.ITEM_FRAME, Material.ITEM_FRAME);
        ENTITY_HEADS.put(EntityType.GLOW_ITEM_FRAME, Material.GLOW_ITEM_FRAME);
        ENTITY_HEADS.put(EntityType.PAINTING, Material.PAINTING);
        ENTITY_HEADS.put(EntityType.LEASH_KNOT, Material.LEAD);
        ENTITY_HEADS.put(EntityType.END_CRYSTAL, Material.END_CRYSTAL);
        ENTITY_HEADS.put(EntityType.LIGHTNING_BOLT, Material.LIGHTNING_ROD);
        ENTITY_HEADS.put(EntityType.AREA_EFFECT_CLOUD, Material.DRAGON_BREATH);
        ENTITY_HEADS.put(EntityType.EVOKER_FANGS, Material.IRON_SWORD);
        ENTITY_HEADS.put(EntityType.MARKER, Material.STRUCTURE_VOID);
        ENTITY_HEADS.put(EntityType.INTERACTION, Material.STRUCTURE_VOID);
        ENTITY_HEADS.put(EntityType.BLOCK_DISPLAY, Material.GLASS);
        ENTITY_HEADS.put(EntityType.ITEM_DISPLAY, Material.ITEM_FRAME);
        ENTITY_HEADS.put(EntityType.TEXT_DISPLAY, Material.OAK_SIGN);
    }
    
    private EntityHeadUtils() {
        // Utility class
    }
    
    /**
     * Gets the material to use as a visual representation of an entity type.
     * 
     * @param type The entity type
     * @return The material to display, or BARRIER if not mapped
     */
    public static Material getMaterial(EntityType type) {
        return ENTITY_HEADS.getOrDefault(type, Material.BARRIER);
    }
    
    /**
     * Formats an entity type name to be more readable.
     * 
     * @param type The entity type
     * @return A formatted display name
     */
    public static String formatName(EntityType type) {
        String name = type.name().toLowerCase().replace("_", " ");
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
}
