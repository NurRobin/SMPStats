package de.nurrobin.smpstats.dashboard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Per-IP token bucket: {@code ratePerSecond} refill, {@code burst} capacity. */
final class IpRateLimiter {
    private static final int MAX_TRACKED = 10_000;
    private static final long STALE_NANOS = 60_000_000_000L;

    private final double ratePerSecond;
    private final double burst;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    IpRateLimiter(double ratePerSecond, int burst) {
        this(ratePerSecond, burst, System::nanoTime);
    }

    IpRateLimiter(double ratePerSecond, int burst, LongSupplier nanoClock) {
        this.ratePerSecond = ratePerSecond;
        this.burst = burst;
        this.nanoClock = nanoClock;
    }

    boolean tryAcquire(String ip) {
        long now = nanoClock.getAsLong();
        if (buckets.size() >= MAX_TRACKED && !buckets.containsKey(ip)) {
            buckets.values().removeIf(b -> now - b.last > STALE_NANOS);
            if (buckets.size() >= MAX_TRACKED) {
                return false;
            }
        }
        Bucket bucket = buckets.computeIfAbsent(ip, k -> new Bucket(burst, now));
        synchronized (bucket) {
            double elapsed = Math.max(0, now - bucket.last) / 1_000_000_000.0;
            bucket.tokens = Math.min(burst, bucket.tokens + elapsed * ratePerSecond);
            bucket.last = now;
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    void clear() {
        buckets.clear();
    }

    private static final class Bucket {
        double tokens;
        long last;

        Bucket(double tokens, long last) {
            this.tokens = tokens;
            this.last = last;
        }
    }
}
