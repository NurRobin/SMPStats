package de.nurrobin.smpstats.database;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteQueueTest {
    @Test
    void inlineRunsOnCaller() {
        WriteQueue queue = WriteQueue.inline();
        Thread[] ran = new Thread[1];
        queue.submit(() -> ran[0] = Thread.currentThread());
        assertEquals(Thread.currentThread(), ran[0]);
    }

    @Test
    void asyncRunsInOrderOnOtherThreadAndShutdownFlushes() {
        WriteQueue queue = WriteQueue.async(Logger.getLogger("test"));
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        Thread[] ran = new Thread[1];
        for (int i = 0; i < 200; i++) {
            int n = i;
            queue.submit(() -> {
                ran[0] = Thread.currentThread();
                order.add(n);
            });
        }
        assertEquals(0, queue.shutdown(5_000));
        assertEquals(200, order.size());
        for (int i = 0; i < 200; i++) {
            assertEquals(i, order.get(i));
        }
        assertNotEquals(Thread.currentThread(), ran[0]);
    }

    @Test
    void failingTaskDoesNotStopTheQueue() {
        WriteQueue queue = WriteQueue.async(Logger.getLogger("test"));
        int[] counter = {0};
        queue.submit(() -> { throw new IllegalStateException("boom"); });
        queue.submit(() -> counter[0]++);
        assertEquals(0, queue.shutdown(5_000));
        assertEquals(1, counter[0]);
    }

    @Test
    void shutdownReportsLostWritesAfterTimeout() throws Exception {
        WriteQueue queue = WriteQueue.async(Logger.getLogger("test"));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        queue.submit(() -> {
            started.countDown();
            try {
                never.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        queue.submit(() -> { });
        queue.submit(() -> { });
        started.await();
        int lost = queue.shutdown(200);
        assertTrue(lost >= 3, "lost=" + lost);
    }

    @Test
    void submitAfterShutdownRunsInline() {
        WriteQueue queue = WriteQueue.async(Logger.getLogger("test"));
        queue.shutdown(1_000);
        int[] c = {0};
        queue.submit(() -> c[0]++);
        assertEquals(1, c[0]);
    }
}
