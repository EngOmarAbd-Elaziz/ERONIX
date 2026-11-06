package hospital.ui;

import hospital.simulation.SimulationEvent;
import hospital.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardTextTest {
    @Test
    void showsTheProductNameAndEnglishSimulationEvents() {
        assertEquals("ERONIX", DashboardText.text("app.title"));
        assertEquals("P1 arrived.", DashboardText.eventMessage(event("P1 arrived.")));
    }

    @Test
    void providesEnglishDashboardLabels() {
        assertEquals("Treatment rooms", DashboardText.text("field.rooms"));
    }

    private static SimulationEvent event(String message) {
        return new SimulationEvent(1, Instant.now(), SimulationSnapshot.EventCategory.PATIENT, message);
    }
}
