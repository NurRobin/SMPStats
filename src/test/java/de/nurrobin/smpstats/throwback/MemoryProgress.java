package de.nurrobin.smpstats.throwback;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory progress for tests. */
final class MemoryProgress implements ProgressRepository {
    final Map<UUID, Progress> rows = new HashMap<>();

    @Override
    public Optional<Progress> find(UUID uuid) {
        return Optional.ofNullable(rows.get(uuid));
    }

    @Override
    public void save(UUID uuid, Progress progress) {
        rows.put(uuid, progress);
    }
}
