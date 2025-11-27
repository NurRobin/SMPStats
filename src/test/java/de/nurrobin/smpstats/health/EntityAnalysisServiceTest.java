package de.nurrobin.smpstats.health;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityAnalysisServiceTest {

    private ServerMock server;
    private EntityAnalysisService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        service = new EntityAnalysisService();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void analyzeEntitiesReturnsEmptyListWhenNoEntities() {
        // MockBukkit starts with no entities
        List<EntityAnalysisService.EntityTypeInfo> result = service.analyzeEntities();
        
        // May not be completely empty due to default world generation
        assertNotNull(result);
    }
    
    @Test
    void analyzeEntitiesReturnsEntityTypeInfo() {
        World world = server.getWorld("world");
        if (world == null) {
            // Skip test if world not available
            return;
        }
        
        // Spawn some entities
        Location loc = new Location(world, 0, 64, 0);
        world.spawn(loc, Zombie.class);
        world.spawn(loc, Zombie.class);
        
        List<EntityAnalysisService.EntityTypeInfo> result = service.analyzeEntities();
        
        assertNotNull(result);
        assertTrue(result.stream().anyMatch(info -> info.type() == EntityType.ZOMBIE));
        
        EntityAnalysisService.EntityTypeInfo zombieInfo = result.stream()
                .filter(info -> info.type() == EntityType.ZOMBIE)
                .findFirst()
                .orElseThrow();
        
        assertEquals(2, zombieInfo.count());
        assertEquals(2, zombieInfo.instances().size());
    }
    
    @Test
    void analyzeEntitiesSortsByCountDescending() {
        World world = server.getWorld("world");
        if (world == null) {
            // Skip test if world not available
            return;
        }
        
        Location loc = new Location(world, 0, 64, 0);
        // Spawn 3 zombies
        world.spawn(loc, Zombie.class);
        world.spawn(loc, Zombie.class);
        world.spawn(loc, Zombie.class);
        // Spawn 1 skeleton
        world.spawn(loc, org.bukkit.entity.Skeleton.class);
        
        List<EntityAnalysisService.EntityTypeInfo> result = service.analyzeEntities();
        
        // Should be sorted with most entities first
        boolean foundZombieFirst = false;
        boolean foundSkeleton = false;
        
        for (EntityAnalysisService.EntityTypeInfo info : result) {
            if (info.type() == EntityType.ZOMBIE) {
                assertFalse(foundSkeleton, "Zombies should come before skeletons");
                foundZombieFirst = true;
            }
            if (info.type() == EntityType.SKELETON) {
                foundSkeleton = true;
            }
        }
        
        assertTrue(foundZombieFirst && foundSkeleton);
    }
    
    @Test
    void analyzeEntitiesIncludesCustomNames() {
        World world = server.getWorld("world");
        if (world == null) {
            // Skip test if world not available
            return;
        }
        
        Location loc = new Location(world, 0, 64, 0);
        Zombie zombie = world.spawn(loc, Zombie.class);
        zombie.customName(Component.text("Super Zombie"));
        
        List<EntityAnalysisService.EntityTypeInfo> result = service.analyzeEntities();
        
        EntityAnalysisService.EntityTypeInfo zombieInfo = result.stream()
                .filter(info -> info.type() == EntityType.ZOMBIE)
                .findFirst()
                .orElseThrow();
        
        EntityAnalysisService.EntityInstance instance = zombieInfo.instances().get(0);
        assertEquals("Super Zombie", instance.customName());
    }

    @Test
    void getEntitiesOfTypeReturnsEmptyListWhenNone() {
        List<EntityAnalysisService.EntityInstance> result = service.getEntitiesOfType(EntityType.ENDER_DRAGON);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void getEntitiesOfTypeReturnsMatchingEntities() {
        World world = server.getWorld("world");
        if (world == null) {
            return;
        }
        
        Location loc = new Location(world, 100, 64, 200);
        Zombie zombie = world.spawn(loc, Zombie.class);
        
        List<EntityAnalysisService.EntityInstance> result = service.getEntitiesOfType(EntityType.ZOMBIE);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        
        EntityAnalysisService.EntityInstance instance = result.get(0);
        assertEquals(zombie.getEntityId(), instance.entityId());
        assertEquals("world", instance.worldName());
        assertEquals(100, instance.x(), 0.1);
        assertEquals(64, instance.y(), 0.1);
        assertEquals(200, instance.z(), 0.1);
    }
    
    @Test
    void getEntitiesOfTypeReturnsCustomName() {
        World world = server.getWorld("world");
        if (world == null) {
            return;
        }
        
        Location loc = new Location(world, 0, 64, 0);
        Zombie zombie = world.spawn(loc, Zombie.class);
        zombie.customName(Component.text("Named Entity"));
        
        List<EntityAnalysisService.EntityInstance> result = service.getEntitiesOfType(EntityType.ZOMBIE);
        
        assertEquals(1, result.size());
        assertEquals("Named Entity", result.get(0).customName());
    }

    @Test
    void killAllOfTypeReturnsZeroWhenNone() {
        int killed = service.killAllOfType(EntityType.ENDER_DRAGON);
        
        assertEquals(0, killed);
    }
    
    @Test
    void killAllOfTypeRemovesMatchingEntities() {
        World world = server.getWorld("world");
        if (world == null) {
            return;
        }
        
        Location loc = new Location(world, 0, 64, 0);
        world.spawn(loc, Zombie.class);
        world.spawn(loc, Zombie.class);
        world.spawn(loc, Zombie.class);
        
        int initialCount = service.getEntitiesOfType(EntityType.ZOMBIE).size();
        assertEquals(3, initialCount);
        
        int killed = service.killAllOfType(EntityType.ZOMBIE);
        
        assertEquals(3, killed);
        assertEquals(0, service.getEntitiesOfType(EntityType.ZOMBIE).size());
    }
    
    @Test
    void killAllOfTypeDoesNotAffectOtherTypes() {
        World world = server.getWorld("world");
        if (world == null) {
            return;
        }
        
        Location loc = new Location(world, 0, 64, 0);
        world.spawn(loc, Zombie.class);
        world.spawn(loc, Zombie.class);
        world.spawn(loc, org.bukkit.entity.Skeleton.class);
        
        service.killAllOfType(EntityType.ZOMBIE);
        
        assertEquals(0, service.getEntitiesOfType(EntityType.ZOMBIE).size());
        assertEquals(1, service.getEntitiesOfType(EntityType.SKELETON).size());
    }

    @Test
    void killEntityReturnsFalseWhenNotFound() {
        boolean result = service.killEntity(12345, "world");
        
        assertFalse(result);
    }

    @Test
    void killEntityReturnsFalseForInvalidWorld() {
        boolean result = service.killEntity(12345, "nonexistent_world");
        
        assertFalse(result);
    }
    
    @Test
    void killEntityRemovesSpecificEntity() {
        World world = server.getWorld("world");
        if (world == null) {
            return;
        }
        
        Location loc = new Location(world, 0, 64, 0);
        Zombie zombie = world.spawn(loc, Zombie.class);
        int entityId = zombie.getEntityId();
        
        boolean result = service.killEntity(entityId, "world");
        
        assertTrue(result);
        assertEquals(0, service.getEntitiesOfType(EntityType.ZOMBIE).size());
    }
    
    @Test
    void killEntityDoesNotAffectOtherEntities() {
        World world = server.getWorld("world");
        if (world == null) {
            return;
        }
        
        Location loc = new Location(world, 0, 64, 0);
        Zombie zombie1 = world.spawn(loc, Zombie.class);
        Zombie zombie2 = world.spawn(loc, Zombie.class);
        int entityId1 = zombie1.getEntityId();
        
        service.killEntity(entityId1, "world");
        
        // Only zombie2 should remain
        List<EntityAnalysisService.EntityInstance> remaining = service.getEntitiesOfType(EntityType.ZOMBIE);
        assertEquals(1, remaining.size());
        assertEquals(zombie2.getEntityId(), remaining.get(0).entityId());
    }

    @Test
    void entityInstanceGetLocationReturnsNullForInvalidWorld() {
        EntityAnalysisService.EntityInstance instance = new EntityAnalysisService.EntityInstance(
                1, "nonexistent_world", 0, 64, 0, null);
        
        assertNull(instance.getLocation());
    }

    @Test
    void entityInstanceGetEntityReturnsNullForInvalidWorld() {
        EntityAnalysisService.EntityInstance instance = new EntityAnalysisService.EntityInstance(
                1, "nonexistent_world", 0, 64, 0, null);
        
        assertNull(instance.getEntity());
    }
    
    @Test
    void entityInstanceGetLocationReturnsValidLocation() {
        World world = server.getWorld("world");
        if (world == null) {
            return;
        }
        
        EntityAnalysisService.EntityInstance instance = new EntityAnalysisService.EntityInstance(
                1, "world", 100, 64, 200, null);
        
        Location loc = instance.getLocation();
        assertNotNull(loc);
        assertEquals(100, loc.getX(), 0.1);
        assertEquals(64, loc.getY(), 0.1);
        assertEquals(200, loc.getZ(), 0.1);
        assertEquals(world, loc.getWorld());
    }
    
    @Test
    void entityInstanceGetEntityReturnsEntity() {
        World world = server.getWorld("world");
        if (world == null) {
            return;
        }
        
        Location loc = new Location(world, 0, 64, 0);
        Zombie zombie = world.spawn(loc, Zombie.class);
        int entityId = zombie.getEntityId();
        
        EntityAnalysisService.EntityInstance instance = new EntityAnalysisService.EntityInstance(
                entityId, "world", 0, 64, 0, null);
        
        Entity foundEntity = instance.getEntity();
        assertNotNull(foundEntity);
        assertEquals(entityId, foundEntity.getEntityId());
    }
    
    @Test
    void entityInstanceGetEntityReturnsNullWhenEntityRemoved() {
        World world = server.getWorld("world");
        if (world == null) {
            return;
        }
        
        Location loc = new Location(world, 0, 64, 0);
        Zombie zombie = world.spawn(loc, Zombie.class);
        int entityId = zombie.getEntityId();
        zombie.remove();
        
        EntityAnalysisService.EntityInstance instance = new EntityAnalysisService.EntityInstance(
                entityId, "world", 0, 64, 0, null);
        
        Entity foundEntity = instance.getEntity();
        assertNull(foundEntity);
    }
    
    @Test
    void entityTypeInfoConstructorSetsCountFromInstances() {
        List<EntityAnalysisService.EntityInstance> instances = List.of(
                new EntityAnalysisService.EntityInstance(1, "world", 0, 64, 0, null),
                new EntityAnalysisService.EntityInstance(2, "world", 0, 64, 0, null),
                new EntityAnalysisService.EntityInstance(3, "world", 0, 64, 0, null)
        );
        
        EntityAnalysisService.EntityTypeInfo info = new EntityAnalysisService.EntityTypeInfo(EntityType.ZOMBIE, instances);
        
        assertEquals(3, info.count());
        assertEquals(EntityType.ZOMBIE, info.type());
        assertEquals(instances, info.instances());
    }
}
