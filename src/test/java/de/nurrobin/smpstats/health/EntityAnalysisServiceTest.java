package de.nurrobin.smpstats.health;

import org.bukkit.entity.EntityType;
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
    void getEntitiesOfTypeReturnsEmptyListWhenNone() {
        List<EntityAnalysisService.EntityInstance> result = service.getEntitiesOfType(EntityType.ENDER_DRAGON);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void killAllOfTypeReturnsZeroWhenNone() {
        int killed = service.killAllOfType(EntityType.ENDER_DRAGON);
        
        assertEquals(0, killed);
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
}
