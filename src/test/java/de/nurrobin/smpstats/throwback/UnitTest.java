package de.nurrobin.smpstats.throwback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitTest {
    @Test
    void hoursFromMilliseconds() {
        assertEquals("30 Stunden", Unit.HOURS.format(30 * 3_600_000.0));
        assertEquals("1,0 Stunde", Unit.HOURS.format(3_600_000.0));
        assertEquals("150 Stunden", Unit.HOURS.format(150 * 3_600_000.0));
    }

    @Test
    void kilometresFromCentimetres() {
        assertEquals("7,3 km", Unit.KM.format(730_000));
        assertEquals("412 km", Unit.KM.format(41_200_000));
    }

    @Test
    void countsUseGermanGrouping() {
        assertEquals("38.412", Unit.COUNT.format(38_412));
    }

    @Test
    void datesAreShownInBerlinTime() {
        assertEquals("15.11.2025", Unit.DATE.format(java.time.Instant.parse("2025-11-15T12:00:00Z").toEpochMilli()));
    }
}
