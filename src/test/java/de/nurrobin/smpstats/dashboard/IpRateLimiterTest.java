package de.nurrobin.smpstats.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRateLimiterTest {
    @Test
    void allowsBurstThenRefillsAtRate() {
        long[] nanos = {0L};
        IpRateLimiter limiter = new IpRateLimiter(10, 20, () -> nanos[0]);
        int allowed = 0;
        for (int i = 0; i < 30; i++) {
            if (limiter.tryAcquire("1.1.1.1")) {
                allowed++;
            }
        }
        assertEquals(20, allowed);
        assertFalse(limiter.tryAcquire("1.1.1.1"));

        nanos[0] += 1_000_000_000L; // one second: 10 tokens
        allowed = 0;
        for (int i = 0; i < 30; i++) {
            if (limiter.tryAcquire("1.1.1.1")) {
                allowed++;
            }
        }
        assertEquals(10, allowed);
    }

    @Test
    void ipsAreIndependent() {
        IpRateLimiter limiter = new IpRateLimiter(1, 1, () -> 0L);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
    }
}
