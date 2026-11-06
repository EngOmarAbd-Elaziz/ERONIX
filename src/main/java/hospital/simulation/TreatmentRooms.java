package hospital.simulation;

import java.util.HashMap;
import java.util.Map;

public final class TreatmentRooms {
    public static final int DEFAULT_ROOM_COUNT = 2;
    public static final long ACQUISITION_TIMEOUT_MILLIS = 3_000;

    private final Semaphore permits;
    private final Object roomLock = new Object();
    private final Map<Integer, Integer> doctorRooms = new HashMap<>();
    private final SimulationState state;

    public TreatmentRooms() {
        this(DEFAULT_ROOM_COUNT, null);
    }

    TreatmentRooms(int roomCount, SimulationState state) {
        if (roomCount < 1) {
            throw new IllegalArgumentException("Treatment room count must be positive.");
        }
        permits = new Semaphore(roomCount);
        this.state = state;
    }

    public boolean tryAcquire(long millis) throws InterruptedException {
        return permits.tryAcquire(millis);
    }

    public void release() {
        permits.release();
    }

    void assign(int doctorId, int patientId, int treatmentMillis) {
        synchronized (roomLock) {
            state.roomAcquired(doctorId, patientId, treatmentMillis);
            doctorRooms.put(doctorId, findRoomForDoctor(doctorId));
        }
    }

    void release(int doctorId, int patientId, boolean treated) {
        synchronized (roomLock) {
            Integer roomNumber = doctorRooms.get(doctorId);
            if (roomNumber == null) {
                throw new IllegalStateException("Doctor " + doctorId + " has no treatment room.");
            }
            permits.release();
            state.releaseRoom(doctorId, patientId, treated);
            doctorRooms.remove(doctorId);
        }
    }

    private int findRoomForDoctor(int doctorId) {
        return state.snapshot().treatmentRooms().stream()
                .filter(room -> room.doctorId() == doctorId)
                .mapToInt(SimulationSnapshot.TreatmentRoom::number)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Acquired room is missing from state."));
    }
}
