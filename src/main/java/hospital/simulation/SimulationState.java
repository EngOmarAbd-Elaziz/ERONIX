package hospital.simulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static hospital.simulation.SimulationSnapshot.DoctorStatus;
import static hospital.simulation.SimulationSnapshot.EventCategory;
import static hospital.simulation.SimulationSnapshot.RoomStatus;
import static hospital.simulation.SimulationSnapshot.Status;

final class SimulationState {
    private final SimulationConfig config;
    private final Logger logger;
    private final List<String> waitingPatients = new ArrayList<>();
    private final Map<Integer, PatientStatus> patientStatuses = new LinkedHashMap<>();
    private final List<DoctorState> doctors;
    private final List<RoomState> rooms;
    private Status status = Status.IDLE;
    private int patientsCompleted;
    private int patientsBeingTreated;

    SimulationState(SimulationConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        rooms = new ArrayList<>(config.treatmentRoomCount());
        doctors = new ArrayList<>(config.doctorCount());
        for (int i = 1; i <= config.doctorCount(); i++) {
            doctors.add(new DoctorState(i));
        }
        for (int i = 1; i <= config.treatmentRoomCount(); i++) {
            rooms.add(new RoomState(i));
        }
    }

    synchronized SimulationSnapshot snapshot() {
        return snapshotLocked();
    }

    synchronized void start() {
        status = Status.RUNNING;
        emit(EventCategory.SYSTEM, "Simulation configuration: waiting room capacity "
                + config.waitingRoomCapacity() + ", " + config.doctorCount()
                + " doctors, " + config.patientCount() + " patients, "
                + config.treatmentRoomCount() + " treatment rooms.");
    }

    synchronized void patientsArriving() {
        emit(EventCategory.SYSTEM, "Patients arriving:");
        StringBuilder names = new StringBuilder();
        for (int id = 1; id <= config.patientCount(); id++) {
            if (id > 1) {
                names.append(' ');
            }
            names.append("P").append(id);
        }
        emit(EventCategory.SYSTEM, names.toString());
    }

    synchronized void patientArrived(int patientId) {
        patientStatuses.put(patientId, PatientStatus.OUTSIDE);
        emit(EventCategory.PATIENT, patientName(patientId) + " arrived.");
    }

    synchronized void waitingRoomFull(int patientId) {
        emit(EventCategory.WARNING, "The waiting room is full.");
        emit(EventCategory.WARNING,
                patientName(patientId) + " is waiting outside for an available seat.");
    }

    synchronized void patientEntered(int patientId) {
        if (patientStatuses.get(patientId) != PatientStatus.OUTSIDE) {
            throw new IllegalStateException(patientName(patientId) + " cannot enter from "
                    + patientStatuses.get(patientId) + ".");
        }
        if (waitingPatients.size() >= config.waitingRoomCapacity()) {
            throw new IllegalStateException("Waiting room capacity invariant violated.");
        }
        patientStatuses.put(patientId, PatientStatus.WAITING);
        waitingPatients.add(patientName(patientId));
        emit(EventCategory.WAITING_ROOM, patientName(patientId) + " entered the waiting room.");
    }

    synchronized void patientAssigned(int patientId, int doctorId) {
        String name = patientName(patientId);
        if (!waitingPatients.remove(name)) {
            throw new IllegalStateException(name + " was not in the waiting room.");
        }
        DoctorState doctor = doctor(doctorId);
        if (doctor.patientId != null) {
            throw new IllegalStateException("Doctor " + doctorId + " already has a patient.");
        }
        patientStatuses.put(patientId, PatientStatus.ASSIGNED);
        doctor.patientId = name;
        doctor.status = DoctorStatus.WAITING_FOR_ROOM;
        emit(EventCategory.DOCTOR, "Doctor " + doctorId + " is assigned to " + name + ".");
    }

    synchronized void patientTakenWithoutDoctor(int patientId) {
        String name = patientName(patientId);
        if (!waitingPatients.remove(name)) {
            throw new IllegalStateException(name + " was not in the waiting room.");
        }
        patientStatuses.put(patientId, PatientStatus.ASSIGNED);
        emit(EventCategory.WAITING_ROOM, name + " was taken from the waiting room.");
    }

    synchronized void tryingForRoom(int doctorId) {
        doctor(doctorId).status = DoctorStatus.WAITING_FOR_ROOM;
        emit(EventCategory.DOCTOR, "Doctor " + doctorId + " is trying to acquire a treatment room.");
    }

    synchronized void roomAcquired(int doctorId, int patientId, int treatmentMillis) {
        DoctorState doctor = doctor(doctorId);
        RoomState room = rooms.stream()
                .filter(candidate -> candidate.status == RoomStatus.AVAILABLE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No treatment room is available."));
        room.status = RoomStatus.OCCUPIED;
        room.doctorId = doctorId;
        room.patientId = patientName(patientId);
        doctor.roomNumber = room.number;
        doctor.status = DoctorStatus.TREATING;
        doctor.treatmentStartedAtMillis = System.currentTimeMillis();
        doctor.treatmentDurationMillis = treatmentMillis;
        patientStatuses.put(patientId, PatientStatus.TREATING);
        patientsBeingTreated++;
        emit(EventCategory.TREATMENT_ROOM, "Doctor " + doctorId + " acquired Treatment Room "
                + room.number + ".");
        emit(EventCategory.DOCTOR, "Doctor " + doctorId + " begins treating " + patientName(patientId) + ".");
    }

    synchronized void treatmentFinished(int doctorId, int patientId) {
        emit(EventCategory.DOCTOR,
                "Doctor " + doctorId + " finished treating " + patientName(patientId) + ".");
    }

    synchronized int releaseRoom(int doctorId, int patientId, boolean treated) {
        DoctorState doctor = doctor(doctorId);
        RoomState room = rooms.get(doctor.roomNumber - 1);
        if (room.status != RoomStatus.OCCUPIED
                || room.doctorId != doctorId
                || !patientName(patientId).equals(room.patientId)) {
            throw new IllegalStateException("Doctor " + doctorId + " does not own the room.");
        }
        room.status = RoomStatus.AVAILABLE;
        room.doctorId = 0;
        room.patientId = null;
        doctor.roomNumber = 0;
        doctor.treatmentStartedAtMillis = 0;
        doctor.treatmentDurationMillis = 0;
        doctor.status = DoctorStatus.AVAILABLE;
        patientStatuses.put(patientId, treated ? PatientStatus.TREATED : PatientStatus.OUTSIDE);
        patientsBeingTreated--;
        emit(EventCategory.TREATMENT_ROOM,
                "Doctor " + doctorId + " released Treatment Room " + room.number + ".");
        return room.number;
    }

    synchronized void treatmentInterrupted(int doctorId, int patientId) {
        emit(EventCategory.WARNING, "Doctor " + doctorId + " was interrupted while treating "
                + patientName(patientId) + ".");
    }

    synchronized void doctorTimedOut(int doctorId, int patientId) {
        emit(EventCategory.WARNING,
                "Doctor " + doctorId + " could not acquire a treatment room.");
        patientStatuses.put(patientId, PatientStatus.OUTSIDE);
        emit(EventCategory.WARNING, "Doctor " + doctorId + " is sending "
                + patientName(patientId) + " back to the waiting room.");
        doctorAvailable(doctorId);
    }

    synchronized void doctorAvailable(int doctorId) {
        DoctorState doctor = doctor(doctorId);
        doctor.patientId = null;
        doctor.status = DoctorStatus.AVAILABLE;
        doctor.roomNumber = 0;
        emit(EventCategory.DOCTOR, "Doctor " + doctorId + " is now available.");
    }

    synchronized void patientLeft(int patientId) {
        if (patientStatuses.get(patientId) != PatientStatus.TREATED) {
            throw new IllegalStateException(patientName(patientId) + " cannot leave before treatment.");
        }
        patientStatuses.put(patientId, PatientStatus.COMPLETED);
        patientsCompleted++;
        emit(EventCategory.SUCCESS, patientName(patientId) + " left.");
    }

    synchronized void doctorStopped(int doctorId) {
        DoctorState doctor = doctor(doctorId);
        doctor.status = DoctorStatus.STOPPED;
        doctor.patientId = null;
        emit(EventCategory.SYSTEM, "Doctor " + doctorId + " is stopping.");
    }

    synchronized void completed() {
        status = Status.COMPLETED;
        emit(EventCategory.SUCCESS, "All patients have been treated.");
        emit(EventCategory.SUCCESS, "Simulation completed.");
    }

    synchronized void stopped() {
        status = Status.STOPPED;
        emit(EventCategory.WARNING, "Simulation stopped.");
    }

    synchronized void failed(RuntimeException exception) {
        emit(EventCategory.WARNING, "Simulation failed: " + exception.getMessage());
    }

    private DoctorState doctor(int doctorId) {
        if (doctorId < 1 || doctorId > doctors.size()) {
            throw new IllegalArgumentException("Unknown doctor: " + doctorId);
        }
        return doctors.get(doctorId - 1);
    }

    private SimulationSnapshot snapshotLocked() {
        List<SimulationSnapshot.Doctor> doctorSnapshots = doctors.stream()
                .map(doctor -> new SimulationSnapshot.Doctor(
                        doctor.id,
                        doctor.status,
                        doctor.patientId,
                        doctor.treatmentStartedAtMillis,
                        doctor.treatmentDurationMillis))
                .toList();
        List<SimulationSnapshot.TreatmentRoom> roomSnapshots = rooms.stream()
                .map(room -> new SimulationSnapshot.TreatmentRoom(
                        room.number, room.status, room.doctorId, room.patientId))
                .toList();
        int outside = (int) patientStatuses.values().stream()
                .filter(patientStatus -> patientStatus == PatientStatus.OUTSIDE)
                .count();
        List<String> outsidePatientIds = patientStatuses.entrySet().stream()
                .filter(entry -> entry.getValue() == PatientStatus.OUTSIDE)
                .map(entry -> patientName(entry.getKey()))
                .toList();
        return new SimulationSnapshot(config, status, waitingPatients, outside, outsidePatientIds,
                patientsBeingTreated, patientsCompleted, doctorSnapshots, roomSnapshots);
    }

    private void emit(EventCategory category, String message) {
        logger.log(snapshotLocked(), category, message);
    }

    private static String patientName(int patientId) {
        return "P" + patientId;
    }

    private enum PatientStatus {
        OUTSIDE, WAITING, ASSIGNED, TREATING, TREATED, COMPLETED
    }

    private static final class DoctorState {
        private final int id;
        private DoctorStatus status = DoctorStatus.IDLE;
        private String patientId;
        private int roomNumber;
        private long treatmentStartedAtMillis;
        private int treatmentDurationMillis;

        private DoctorState(int id) {
            this.id = id;
        }
    }

    private static final class RoomState {
        private final int number;
        private RoomStatus status = RoomStatus.AVAILABLE;
        private int doctorId;
        private String patientId;

        private RoomState(int number) {
            this.number = number;
        }
    }
}
