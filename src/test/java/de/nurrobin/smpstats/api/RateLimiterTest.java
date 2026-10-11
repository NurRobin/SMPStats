package de.nurrobin.smpstats.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {
    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong now = new AtomicLong(5 * SECOND);

    @Test
    void allowsABurstOfTheConfiguredSizeThenRefuses() {
        RateLimiter limiter = new RateLimiter(3, now::get);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void refillsWithTimeButNeverAboveTheBurst() {
        RateLimiter limiter = new RateLimiter(2, now::get);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));

        now.addAndGet(SECOND / 2);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));

        now.addAndGet(60 * SECOND);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void clientsDoNotShareABucket() {
        RateLimiter limiter = new RateLimiter(1, now::get);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void clockGoingBackwardsDoesNotGrantTokens() {
        RateLimiter limiter = new RateLimiter(1, now::get);
        assertTrue(limiter.tryAcquire("a"));
        now.addAndGet(-10 * SECOND);
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void idleClientsAreForgottenOnceTheTableIsLarge() {
        RateLimiter limiter = new RateLimiter(5, now::get);
        for (int i = 0; i <= RateLimiter.SWEEP_THRESHOLD; i++) {
            assertTrue(limiter.tryAcquire("client-" + i));
        }
        assertEquals(RateLimiter.SWEEP_THRESHOLD + 1, limiter.trackedClients());

        now.addAndGet(10 * SECOND);
        assertTrue(limiter.tryAcquire("late"));
        assertEquals(1, limiter.trackedClients());
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        assertEquals(1L, new RateLimiter(20).retryAfterSeconds());
        assertEquals(1L, new RateLimiter(1).retryAfterSeconds());
    }

    @Test
    void rejectsNonPositiveRates() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0));
    }
}
