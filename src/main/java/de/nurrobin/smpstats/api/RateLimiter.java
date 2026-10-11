package de.nurrobin.smpstats.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class RateLimiter {
    static final int SWEEP_THRESHOLD = 10_000;

    private final double ratePerSecond;
    private final double capacity;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int requestsPerSecond) {
        this(requestsPerSecond, System::nanoTime);
    }

    RateLimiter(int requestsPerSecond, LongSupplier nanoClock) {
        if (requestsPerSecond < 1) {
            throw new IllegalArgumentException("requestsPerSecond must be at least 1");
        }
        this.ratePerSecond = requestsPerSecond;
        this.capacity = requestsPerSecond;
        this.nanoClock = nanoClock;
    }

    public boolean tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        if (buckets.size() > SWEEP_THRESHOLD) {
            sweep(now);
        }
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, now)).tryTake(now);
    }

    public long retryAfterSeconds() {
        return Math.max(1L, (long) Math.ceil(1.0 / ratePerSecond));
    }

    int trackedClients() {
        return buckets.size();
    }

    private void sweep(long now) {
        buckets.values().removeIf(bucket -> bucket.isFullAt(now));
    }

    private final class Bucket {
        private double tokens;
        private long last;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.last = now;
        }

        synchronized boolean tryTake(long now) {
            refill(now);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized boolean isFullAt(long now) {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(long now) {
            long elapsed = now - last;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed / 1_000_000_000.0 * ratePerSecond);
                last = now;
            }
        }
    }
}
