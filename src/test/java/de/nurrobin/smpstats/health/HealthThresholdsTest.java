package de.nurrobin.smpstats.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthThresholdsTest {
    
    @Test
    void defaultsReturnsValidThresholds() {
        HealthThresholds thresholds = HealthThresholds.defaults();
        
        assertNotNull(thresholds.getTps());
        assertNotNull(thresholds.getMemory());
        assertNotNull(thresholds.getChunks());
        assertNotNull(thresholds.getEntities());
        assertNotNull(thresholds.getHoppers());
        assertNotNull(thresholds.getRedstone());
        assertNotNull(thresholds.getCostIndex());
    }
    
    @Test
    void tpsThresholdHigherIsBetter() {
        HealthThresholds thresholds = HealthThresholds.defaults();
        HealthThresholds.MetricThreshold tps = thresholds.getTps();
        
        assertTrue(tps.higherIsBetter());
        
        // TPS of 20 should be good
        assertEquals(1.0, tps.getQuality(20.0));
        assertEquals("Good", tps.getStatus(20.0));
        
        // TPS of 19 should be good
        assertEquals(1.0, tps.getQuality(19.0));
        
        // TPS of 18 should be acceptable
        assertEquals(0.75, tps.getQuality(18.0));
        assertEquals("Acceptable", tps.getStatus(18.0));
        
        // TPS of 15 should be warning
        assertEquals(0.5, tps.getQuality(15.0));
        assertEquals("Warning", tps.getStatus(15.0));
        
        // TPS of 10 should be bad
        assertEquals(0.25, tps.getQuality(10.0));
        assertEquals("Bad", tps.getStatus(10.0));
        
        // TPS of 5 should be critical
        assertEquals(0.0, tps.getQuality(5.0));
        assertEquals("Critical", tps.getStatus(5.0));
    }
    
    @Test
    void entitiesThresholdLowerIsBetter() {
        HealthThresholds thresholds = HealthThresholds.defaults();
        HealthThresholds.MetricThreshold entities = thresholds.getEntities();
        
        assertFalse(entities.higherIsBetter());
        
        // 100 entities should be good
        assertEquals(1.0, entities.getQuality(100));
        assertEquals("Good", entities.getStatus(100));
        
        // 500 entities should be good (boundary)
        assertEquals(1.0, entities.getQuality(500));
        
        // 1500 entities should be acceptable (boundary)
        assertEquals(0.75, entities.getQuality(1500));
        assertEquals("Acceptable", entities.getStatus(1500));
        
        // 3000 entities should be warning (boundary)
        assertEquals(0.5, entities.getQuality(3000));
        assertEquals("Warning", entities.getStatus(3000));
        
        // 5000 entities should be bad (boundary)
        assertEquals(0.25, entities.getQuality(5000));
        assertEquals("Bad", entities.getStatus(5000));
        
        // 10000 entities should be critical
        assertEquals(0.0, entities.getQuality(10000));
        assertEquals("Critical", entities.getStatus(10000));
    }
    
    @Test
    void memoryPercentThreshold() {
        HealthThresholds thresholds = HealthThresholds.defaults();
        HealthThresholds.MetricThreshold memory = thresholds.getMemory();
        
        assertFalse(memory.higherIsBetter());
        
        // 30% should be good
        assertEquals(1.0, memory.getQuality(30));
        
        // 70% should be acceptable (boundary)
        assertEquals(0.75, memory.getQuality(70));
        
        // 85% should be warning (boundary)
        assertEquals(0.5, memory.getQuality(85));
        
        // 95% should be bad (boundary)
        assertEquals(0.25, memory.getQuality(95));
        
        // 99% should be critical
        assertEquals(0.0, memory.getQuality(99));
    }
    
    @Test
    void customThresholdWorks() {
        HealthThresholds.MetricThreshold custom = new HealthThresholds.MetricThreshold(
                100, 200, 300, 400, false);
        
        assertEquals(1.0, custom.getQuality(50));   // Below good
        assertEquals(1.0, custom.getQuality(100));  // At good
        assertEquals(0.75, custom.getQuality(150)); // Between good and acceptable
        assertEquals(0.75, custom.getQuality(200)); // At acceptable
        assertEquals(0.5, custom.getQuality(250));  // Between acceptable and warning
        assertEquals(0.5, custom.getQuality(300));  // At warning
        assertEquals(0.25, custom.getQuality(350)); // Between warning and critical
        assertEquals(0.25, custom.getQuality(400)); // At critical
        assertEquals(0.0, custom.getQuality(500));  // Above critical
    }
}
