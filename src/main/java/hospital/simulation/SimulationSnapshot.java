package hospital.simulation;

import java.util.List;

public record SimulationSnapshot(
        SimulationConfig config,
        Status status,
        List<String> waitingPatients,
        int patientsOutside,
        List<String> outsidePatientIds,
        int patientsBeingTreated,
        int patientsCompleted,
        List<Doctor> doctors,
        List<TreatmentRoom> treatmentRooms) {

    public SimulationSnapshot {
        waitingPatients = List.copyOf(waitingPatients);
        outsidePatientIds = List.copyOf(outsidePatientIds);
        doctors = List.copyOf(doctors);
        treatmentRooms = List.copyOf(treatmentRooms);
    }

    public enum Status {
        IDLE, RUNNING, COMPLETED, STOPPED
    }

    public enum DoctorStatus {
        IDLE, WAITING_FOR_ROOM, TREATING, AVAILABLE, STOPPED
    }

    public enum RoomStatus {
        AVAILABLE, OCCUPIED
    }

    public enum EventCategory {
        SYSTEM, PATIENT, WAITING_ROOM, DOCTOR, TREATMENT_ROOM, WARNING, SUCCESS
    }

    public record Doctor(
            int id,
            DoctorStatus status,
            String patientId,
            long treatmentStartedAtMillis,
            int treatmentDurationMillis) {
    }

    public record TreatmentRoom(
            int number,
            RoomStatus status,
            int doctorId,
            String patientId) {
    }

    public int patientsWaiting() {
        return waitingPatients.size();
    }

    public int availableDoctors() {
        return (int) doctors.stream()
                .filter(doctor -> doctor.status() == DoctorStatus.IDLE
                        || doctor.status() == DoctorStatus.AVAILABLE)
                .count();
    }

    public int busyDoctors() {
        return doctors.size() - availableDoctors();
    }

    public int availableTreatmentRooms() {
        return (int) treatmentRooms.stream()
                .filter(room -> room.status() == RoomStatus.AVAILABLE)
                .count();
    }
}
