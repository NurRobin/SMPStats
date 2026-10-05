package de.nurrobin.smpstats.season;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Local check against the real 2025 copy; never runs in CI because the data is personal. */
@EnabledIfEnvironmentVariable(named = "SMPSTATS_SEASON_DIR", matches = ".+")
class SeasonRealDataTest {

    @Test
    void importsTheRealSeason() {
        SeasonImporter.Result r = new SeasonImporter().read("2025", Path.of(System.getenv("SMPSTATS_SEASON_DIR")));

        Set<String> keys = new HashSet<>();
        r.data().stats().values().forEach(m -> keys.addAll(m.keySet()));
        System.out.println("players=" + r.data().players().size() + " keys=" + keys.size()
                + " problems=" + r.problems());

        assertEquals(69, r.data().players().size());
        assertTrue(keys.size() > 4000, "keys=" + keys.size());
        assertTrue(r.data().players().values().stream().noneMatch(p -> "unbekannt".equals(p.name())));
        assertTrue(r.problems().stream().noneMatch(p -> p.startsWith("stats.db")), r.problems().toString());
        assertTrue(keys.contains("smpstats:blocks_broken"), "legacy DB values missing");
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("smpstats:moments/")), "legacy moments missing");
    }
}
