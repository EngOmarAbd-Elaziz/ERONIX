package hospital.ui;

import hospital.simulation.SimulationEvent;
import hospital.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static hospital.simulation.SimulationSnapshot.DoctorStatus;
import static hospital.simulation.SimulationSnapshot.EventCategory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HospitalAppOptimizationTest {
    @Test
    void findsDoctorsByFastIndexAndFallbackScan() {
        SimulationSnapshot.Doctor d1 = new SimulationSnapshot.Doctor(
                1, DoctorStatus.AVAILABLE, null, 0, 0);
        SimulationSnapshot.Doctor d2 = new SimulationSnapshot.Doctor(
                2, DoctorStatus.TREATING, "P1", 100L, 1_000);
        SimulationSnapshot.Doctor d5 = new SimulationSnapshot.Doctor(
                5, DoctorStatus.IDLE, null, 0, 0);

        List<SimulationSnapshot.Doctor> ordered = List.of(d1, d2);
        assertEquals(d1, HospitalApp.findDoctor(ordered, 1));
        assertEquals(d2, HospitalApp.findDoctor(ordered, 2));
        assertNull(HospitalApp.findDoctor(ordered, 3));

        List<SimulationSnapshot.Doctor> sparse = List.of(d5, d2);
        assertEquals(d5, HospitalApp.findDoctor(sparse, 5));
        assertEquals(d2, HospitalApp.findDoctor(sparse, 2));
    }

    @Test
    void trimsDisplayedEventsToRetentionLimitWhileKeepingLatestOrder() {
        List<SimulationEvent> displayed = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            displayed.add(new SimulationEvent(
                    i, Instant.ofEpochMilli(i), EventCategory.SYSTEM, "Event " + i));
        }

        HospitalApp.trimDisplayedEvents(displayed, 4);

        assertEquals(4, displayed.size());
        assertEquals(7L, displayed.getFirst().sequence());
        assertEquals(10L, displayed.getLast().sequence());
    }

    @Test
    void computesBoundedScrollTargetForLiveOperationsSection() {
        assertEquals(0.478, HospitalApp.computeScrollTargetVvalue(500, 22, 1900, 900), 0.0001);
        assertEquals(1.0, HospitalApp.computeScrollTargetVvalue(1200, 22, 1900, 900), 0.0001);
        assertEquals(0.0, HospitalApp.computeScrollTargetVvalue(10, 22, 1900, 900), 0.0001);
        assertEquals(0.0, HospitalApp.computeScrollTargetVvalue(500, 22, 800, 900), 0.0001);
    }
}
