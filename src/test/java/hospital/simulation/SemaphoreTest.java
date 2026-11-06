package hospital.simulation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemaphoreTest {
    @Test
    void timedAcquireReturnsImmediatelyWhenPermitExists() throws InterruptedException {
        Semaphore semaphore = new Semaphore(1);

        assertTrue(semaphore.tryAcquire(3_000));
        assertFalse(semaphore.tryAcquire(0));
    }

    @Test
    void timedAcquireBlocksThenTimesOutWithoutBusyWaiting() throws InterruptedException {
        Semaphore semaphore = new Semaphore(0);
        long start = System.nanoTime();

        assertFalse(semaphore.tryAcquire(80));

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMillis >= 60, "Timed acquire returned too early: " + elapsedMillis + "ms");
    }

    @Test
    void acquireWakesWhenAnotherThreadReleases() throws InterruptedException {
        Semaphore semaphore = new Semaphore(0);
        CountDownLatch acquired = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try {
                semaphore.acquire();
                acquired.countDown();
            } catch (InterruptedException exception) {
                interrupted.set(true);
            }
        });

        waiter.start();
        semaphore.release();
        assertTrue(acquired.await(1, TimeUnit.SECONDS));
        waiter.join();
        assertFalse(interrupted.get());
    }

    @Test
    void acquirePropagatesInterruptionWhileBlocked() throws InterruptedException {
        Semaphore semaphore = new Semaphore(0);
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                semaphore.acquire();
            } catch (InterruptedException exception) {
                interrupted.set(true);
            }
        });

        waiter.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        waiter.interrupt();
        waiter.join(1_000);

        assertFalse(waiter.isAlive());
        assertTrue(interrupted.get());
    }

    @Test
    void timedAcquireHonorsInterruptionEvenWhenAPermitIsAvailable() throws InterruptedException {
        Semaphore semaphore = new Semaphore(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                semaphore.tryAcquire(0);
            } catch (InterruptedException exception) {
                interrupted.set(true);
            }
        });

        waiter.start();
        waiter.join(1_000);

        assertTrue(interrupted.get());
        assertTrue(semaphore.tryAcquire(0));
    }
}
