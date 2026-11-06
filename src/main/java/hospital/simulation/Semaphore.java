package hospital.simulation;

public final class Semaphore {
    private long permits;

    public Semaphore(int initialPermits) {
        if (initialPermits < 0) {
            throw new IllegalArgumentException("Initial permits cannot be negative.");
        }
        permits = initialPermits;
    }

    public synchronized void release() {
        if (permits == Long.MAX_VALUE) {
            throw new IllegalStateException("Semaphore permit count overflow.");
        }
        permits++;
        notifyAll();
    }

    public synchronized void acquire() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        while (permits == 0) {
            wait();
        }
        permits--;
    }

    public synchronized boolean tryAcquire(long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative.");
        }
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (permits > 0) {
            permits--;
            return true;
        }
        if (millis == 0) {
            return false;
        }

        long start = System.nanoTime();
        long timeoutNanos = millis > Long.MAX_VALUE / 1_000_000L
                ? Long.MAX_VALUE
                : millis * 1_000_000L;
        long remaining = timeoutNanos;
        while (permits == 0 && remaining > 0) {
            long waitMillis = remaining / 1_000_000L;
            int waitNanos = (int) (remaining % 1_000_000L);
            wait(waitMillis, waitNanos);
            long elapsed = System.nanoTime() - start;
            remaining = elapsed < 0 || elapsed >= timeoutNanos ? 0 : timeoutNanos - elapsed;
        }
        if (permits == 0) {
            return false;
        }
        permits--;
        return true;
    }
}
