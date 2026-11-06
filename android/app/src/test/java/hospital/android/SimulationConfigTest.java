package hospital.android;

import org.junit.Test;

import hospital.simulation.SimulationConfig;

import static org.junit.Assert.assertEquals;

public final class SimulationConfigTest {
    @Test
    public void defaultConfigurationUsesTwoTreatmentRooms() {
        SimulationConfig config = SimulationConfig.defaults(5, 3, 12);

        assertEquals(2, config.treatmentRoomCount());
        assertEquals(150, config.minimumArrivalIntervalMillis());
        assertEquals(700, config.maximumArrivalIntervalMillis());
    }

    @Test
    public void supportsAndroidUiRoomCountConfiguration() {
        SimulationConfig config = new SimulationConfig(
                5, 3, 12, 4, 150, 700, 1_000, 5_000);

        assertEquals(4, config.treatmentRoomCount());
    }
}
