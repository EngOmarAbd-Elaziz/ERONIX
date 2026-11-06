package hospital.simulation;

import java.util.ArrayDeque;
import java.util.Queue;

public final class WaitingRoom {
    private final int capacity;
    private final Queue<Patient> queue = new ArrayDeque<>();
    private final Semaphore empty;
    private final Semaphore full = new Semaphore(0);
    private final Semaphore mutex = new Semaphore(1);
    private final SimulationState state;
    private boolean closed;

    public WaitingRoom(int capacity) {
        this(capacity, null);
    }

    WaitingRoom(int capacity, SimulationState state) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Waiting room capacity must be positive.");
        }
        this.capacity = capacity;
        this.state = state;
        empty = new Semaphore(capacity);
    }

    public void enter(Patient patient) throws InterruptedException {
        if (!empty.tryAcquire(0)) {
            if (state != null) {
                state.waitingRoomFull(patient.getPatientId());
            }
            empty.acquire();
        }

        boolean mutexAcquired = false;
        boolean inserted = false;
        try {
            mutex.acquire();
            mutexAcquired = true;
            if (closed) {
                throw new IllegalStateException("Cannot enter a closed waiting room.");
            }
            if (queue.size() >= capacity) {
                throw new IllegalStateException("Waiting room capacity invariant violated.");
            }
            queue.add(patient);
            inserted = true;
            if (state != null) {
                state.patientEntered(patient.getPatientId());
            }
        } catch (InterruptedException | RuntimeException exception) {
            if (!inserted) {
                empty.release();
            }
            throw exception;
        } finally {
            if (mutexAcquired) {
                mutex.release();
            }
        }
        full.release();
    }

    public Patient take() throws InterruptedException {
        return take(0);
    }

    Patient take(int doctorId) throws InterruptedException {
        full.acquire();
        boolean mutexAcquired = false;
        try {
            mutex.acquire();
            mutexAcquired = true;
            if (queue.isEmpty()) {
                if (closed) {
                    return null;
                }
                throw new IllegalStateException("Waiting-room item semaphore is inconsistent.");
            }
            Patient patient = queue.remove();
            if (state != null && doctorId > 0) {
                state.patientAssigned(patient.getPatientId(), doctorId);
            } else if (state != null) {
                state.patientTakenWithoutDoctor(patient.getPatientId());
            }
            empty.release();
            return patient;
        } catch (InterruptedException | RuntimeException exception) {
            if (!mutexAcquired) {
                full.release();
            }
            throw exception;
        } finally {
            if (mutexAcquired) {
                mutex.release();
            }
        }
    }

    public void closeFor(int doctorCount) throws InterruptedException {
        if (doctorCount < 1) {
            throw new IllegalArgumentException("Doctor count must be positive.");
        }
        mutex.acquire();
        try {
            if (!queue.isEmpty()) {
                throw new IllegalStateException("Cannot close while patients remain in the waiting room.");
            }
            if (!closed) {
                closed = true;
                for (int i = 0; i < doctorCount; i++) {
                    full.release();
                }
            }
        } finally {
            mutex.release();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    SimulationState getState() {
        return state;
    }
}
