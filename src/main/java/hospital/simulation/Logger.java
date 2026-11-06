package hospital.simulation;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class Logger {
    private final AtomicLong sequence = new AtomicLong();
    private final CopyOnWriteArrayList<SimulationObserver> observers = new CopyOnWriteArrayList<>();
    private volatile boolean consoleEnabled = true;

    public static synchronized void log(String message) {
        System.out.println(message);
    }

    void addObserver(SimulationObserver observer) {
        observers.add(observer);
    }

    void setConsoleEnabled(boolean enabled) {
        consoleEnabled = enabled;
    }

    void log(
            SimulationSnapshot snapshot,
            SimulationSnapshot.EventCategory category,
            String message) {
        SimulationEvent event = new SimulationEvent(
                sequence.incrementAndGet(), Instant.now(), category, message);
        if (consoleEnabled) {
            Logger.log(message);
        }
        for (SimulationObserver observer : observers) {
            observer.onUpdate(snapshot, event);
        }
    }
}
