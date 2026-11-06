package hospital.simulation;

import java.time.Instant;

public record SimulationEvent(
        long sequence,
        Instant timestamp,
        SimulationSnapshot.EventCategory category,
        String message) {
}
