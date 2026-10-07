package de.nurrobin.smpstats.throwback;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Keeps progress in memory so the main thread never waits for SQLite. Entries stay for the whole run (one small
 * record per player ever seen), because a quick relog must not read a row the writer has not flushed yet. Rows are loaded off-thread before the
 * player joins ({@link #preload}) and written behind on a single thread, which keeps writes in order.
 */
public final class CachedProgressRepository implements ProgressRepository {
    private final ThrowbackStore store;
    private final Logger logger;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SMPStats-throwback-writer");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, Optional<Progress>> cache = new ConcurrentHashMap<>();

    public CachedProgressRepository(ThrowbackStore store, Logger logger) {
        this.store = store;
        this.logger = logger;
    }

    /** Blocking read, meant for the async pre-login thread. */
    public void preload(UUID uuid) {
        cache.computeIfAbsent(uuid, this::load);
    }

    private Optional<Progress> load(UUID uuid) {
        try {
            return store.find(uuid);
        } catch (SQLException e) {
            logger.warning("Throwback: Fortschritt von " + uuid + " nicht lesbar: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Progress> find(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    @Override
    public void save(UUID uuid, Progress progress) {
        cache.put(uuid, Optional.of(progress));
        writer.execute(() -> {
            try {
                store.save(uuid, progress);
            } catch (SQLException e) {
                logger.warning("Throwback: Fortschritt von " + uuid + " nicht gespeichert: " + e.getMessage());
            }
        });
    }

    /** Forgets a player's row everywhere, used by the admin reset. */
    public void forget(UUID uuid) {
        cache.put(uuid, Optional.empty());
        writer.execute(() -> {
            try {
                store.delete(uuid);
            } catch (SQLException e) {
                logger.warning("Throwback: Fortschritt von " + uuid + " nicht gelöscht: " + e.getMessage());
            }
        });
    }

    /** Waits for pending writes; called on disable before the database is closed. */
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Throwback: ausstehende Schreibvorgänge nicht rechtzeitig beendet");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
