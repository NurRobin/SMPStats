package de.nurrobin.smpstats.health;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for analyzing entities across all worlds.
 * Provides entity type breakdown and individual entity management.
 */
public class EntityAnalysisService {
    
    /**
     * Represents a snapshot of entity data for a specific entity type.
     */
    public record EntityTypeInfo(EntityType type, int count, List<EntityInstance> instances) {
        public EntityTypeInfo(EntityType type, List<EntityInstance> instances) {
            this(type, instances.size(), instances);
        }
    }
    
    /**
     * Represents a single entity instance with its location.
     */
    public record EntityInstance(int entityId, String worldName, double x, double y, double z, String customName) {
        public Location getLocation() {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            return new Location(world, x, y, z);
        }
        
        public Entity getEntity() {
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;
            for (Entity e : world.getEntities()) {
                if (e.getEntityId() == entityId) {
                    return e;
                }
            }
            return null;
        }
    }
    
    /**
     * Analyzes all entities across all worlds and returns a breakdown by type.
     * Excludes players from the analysis.
     * 
     * @return List of EntityTypeInfo sorted by count (highest first)
     */
    public List<EntityTypeInfo> analyzeEntities() {
        Map<EntityType, List<EntityInstance>> entityMap = new EnumMap<>(EntityType.class);
        
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                // Skip players
                if (entity instanceof Player) continue;
                
                EntityType type = entity.getType();
                Location loc = entity.getLocation();
                
                String customName = null;
                if (entity.customName() != null) {
                    customName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(entity.customName());
                }
                
                EntityInstance instance = new EntityInstance(
                        entity.getEntityId(),
                        world.getName(),
                        loc.getX(),
                        loc.getY(),
                        loc.getZ(),
                        customName
                );
                
                entityMap.computeIfAbsent(type, k -> new ArrayList<>()).add(instance);
            }
        }
        
        return entityMap.entrySet().stream()
                .map(e -> new EntityTypeInfo(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(EntityTypeInfo::count).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all instances of a specific entity type.
     * 
     * @param type The entity type to get instances for
     * @return List of EntityInstance for the given type
     */
    public List<EntityInstance> getEntitiesOfType(EntityType type) {
        List<EntityInstance> instances = new ArrayList<>();
        
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getType() != type) continue;
                if (entity instanceof Player) continue;
                
                Location loc = entity.getLocation();
                String customName = null;
                if (entity.customName() != null) {
                    customName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(entity.customName());
                }
                
                instances.add(new EntityInstance(
                        entity.getEntityId(),
                        world.getName(),
                        loc.getX(),
                        loc.getY(),
                        loc.getZ(),
                        customName
                ));
            }
        }
        
        return instances;
    }
    
    /**
     * Removes all entities of a specific type from all worlds.
     * 
     * @param type The entity type to remove
     * @return The number of entities removed
     */
    public int killAllOfType(EntityType type) {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getType() == type && !(entity instanceof Player)) {
                    entity.remove();
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * Removes a specific entity by its ID.
     * 
     * @param entityId The entity ID
     * @param worldName The world name
     * @return true if the entity was found and removed
     */
    public boolean killEntity(int entityId, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;
        
        for (Entity entity : world.getEntities()) {
            if (entity.getEntityId() == entityId && !(entity instanceof Player)) {
                entity.remove();
                return true;
            }
        }
        return false;
    }
}
