package hospital.simulation;

public record SimulationConfig(
        int waitingRoomCapacity,
        int doctorCount,
        int patientCount,
        int treatmentRoomCount,
        int minimumArrivalIntervalMillis,
        int maximumArrivalIntervalMillis,
        int minimumTreatmentMillis,
        int maximumTreatmentMillis) {

    public SimulationConfig {
        requireRange("waiting room capacity", waitingRoomCapacity, 1, 1000);
        requireRange("doctor count", doctorCount, 1, 100);
        requireRange("patient count", patientCount, 1, 10_000);
        requireRange("treatment room count", treatmentRoomCount, 1, 20);
        requireRange("minimum arrival interval", minimumArrivalIntervalMillis, 0, 60_000);
        requireRange("maximum arrival interval", maximumArrivalIntervalMillis, 0, 60_000);
        requireRange("minimum treatment duration", minimumTreatmentMillis, 1, 5_000);
        requireRange("maximum treatment duration", maximumTreatmentMillis, 1, 5_000);
        if (minimumArrivalIntervalMillis > maximumArrivalIntervalMillis) {
            throw new IllegalArgumentException("Minimum arrival interval cannot exceed maximum.");
        }
        if (minimumTreatmentMillis > maximumTreatmentMillis) {
            throw new IllegalArgumentException("Minimum treatment duration cannot exceed maximum.");
        }
    }

    public SimulationConfig(
            int waitingRoomCapacity,
            int doctorCount,
            int patientCount,
            int minimumArrivalIntervalMillis,
            int maximumArrivalIntervalMillis,
            int minimumTreatmentMillis,
            int maximumTreatmentMillis) {
        this(waitingRoomCapacity, doctorCount, patientCount, 2,
                minimumArrivalIntervalMillis, maximumArrivalIntervalMillis,
                minimumTreatmentMillis, maximumTreatmentMillis);
    }

    public static SimulationConfig defaults(int capacity, int doctors, int patients) {
        return new SimulationConfig(capacity, doctors, patients, 2,
                150, 700, 1_000, 5_000);
    }

    private static void requireRange(String name, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum + ".");
        }
    }
}
