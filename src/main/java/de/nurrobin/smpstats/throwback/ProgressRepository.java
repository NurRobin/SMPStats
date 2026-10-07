package de.nurrobin.smpstats.throwback;

import java.util.Optional;
import java.util.UUID;

/** Where {@link ThrowbackFlow} keeps its progress; the plugin backs it with SQLite and a write-behind cache. */
public interface ProgressRepository {
    Optional<Progress> find(UUID uuid);

    void save(UUID uuid, Progress progress);
}
