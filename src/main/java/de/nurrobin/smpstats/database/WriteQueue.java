package de.nurrobin.smpstats.database;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-writer queue for database writes that must not block the main thread (quit saves,
 * death replays). Tasks run in submission order on one thread. {@link #inline()} runs every task
 * directly on the caller, which keeps unit tests deterministic.
 */
public final class WriteQueue {
    private final ExecutorService executor;
    private final Logger logger;
    private final AtomicInteger pending = new AtomicInteger();
    private volatile boolean closed;

    private WriteQueue(ExecutorService executor, Logger logger) {
        this.executor = executor;
        this.logger = logger;
    }

    public static WriteQueue inline() {
        return new WriteQueue(null, Logger.getLogger("SMPStats"));
    }

    public static WriteQueue async(Logger logger) {
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SMPStats-Writer");
            t.setDaemon(true);
            return t;
        });
        return new WriteQueue(exec, logger);
    }

    public void submit(Runnable task) {
        if (executor == null || closed) {
            run(task);
            return;
        }
        pending.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    run(task);
                } finally {
                    pending.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            pending.decrementAndGet();
            run(task);
        }
    }

    public int pendingCount() {
        return pending.get();
    }

    /**
     * Stops accepting queued work, waits for what is queued and returns the number of writes that
     * did not finish within the timeout (0 on a clean flush).
     */
    public int shutdown(long timeoutMillis) {
        if (executor == null) {
            return 0;
        }
        closed = true;
        executor.shutdown();
        boolean done = false;
        try {
            done = executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (done) {
            return 0;
        }
        int lost = pending.get();
        executor.shutdownNow();
        logger.warning("Write queue did not flush within " + timeoutMillis + " ms; " + lost + " write(s) lost.");
        return lost;
    }

    private void run(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Queued database write failed: " + t.getMessage(), t);
        }
    }
}
