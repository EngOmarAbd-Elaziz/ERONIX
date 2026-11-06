package hospital.simulation;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public final class HospitalSimulation {
    private final SimulationConfig config;
    private final Logger logger = new Logger();
    private final SimulationState state;
    private final WaitingRoom waitingRoom;
    private final TreatmentRooms treatmentRooms;
    private final List<Doctor> doctors = new CopyOnWriteArrayList<>();
    private final List<Patient> patients = new CopyOnWriteArrayList<>();
    private final CountDownLatch doctorReady;
    private volatile boolean stopRequested;
    private Thread coordinator;

    public HospitalSimulation(SimulationConfig config) {
        this.config = config;
        state = new SimulationState(config, logger);
        waitingRoom = new WaitingRoom(config.waitingRoomCapacity(), state);
        treatmentRooms = new TreatmentRooms(config.treatmentRoomCount(), state);
        doctorReady = new CountDownLatch(config.doctorCount());
    }

    public synchronized void addObserver(SimulationObserver observer) {
        if (coordinator != null) {
            throw new IllegalStateException("Observers must be registered before starting.");
        }
        logger.addObserver(observer);
    }

    public synchronized void setConsoleEnabled(boolean enabled) {
        if (coordinator != null) {
            throw new IllegalStateException("Console output must be configured before starting.");
        }
        logger.setConsoleEnabled(enabled);
    }

    public synchronized void start() {
        if (coordinator != null) {
            throw new IllegalStateException("A simulation can only be started once.");
        }
        coordinator = new Thread(this::runSimulation, "HospitalSimulation");
        coordinator.start();
    }

    public SimulationSnapshot snapshot() {
        return state.snapshot();
    }

    public void awaitCompletion() throws InterruptedException {
        Thread thread;
        synchronized (this) {
            thread = coordinator;
        }
        if (thread == null) {
            throw new IllegalStateException("Simulation has not been started.");
        }
        thread.join();
    }

    public void stop() {
        stopRequested = true;
        Thread runningCoordinator;
        synchronized (this) {
            runningCoordinator = coordinator;
        }
        if (runningCoordinator != null) {
            runningCoordinator.interrupt();
        }
        for (Patient patient : patients) {
            patient.interrupt();
        }
        for (Doctor doctor : doctors) {
            doctor.interrupt();
        }
    }

    private void runSimulation() {
        boolean completed = false;
        boolean interrupted = false;
        try {
            for (int id = 1; id <= config.doctorCount(); id++) {
                Doctor doctor = new Doctor(id, waitingRoom, treatmentRooms,
                        config, state, doctorReady);
                doctors.add(doctor);
                doctor.start();
            }
            doctorReady.await();
            if (stopRequested) {
                throw new InterruptedException("Simulation stopped.");
            }

            state.start();
            state.patientsArriving();
            for (int id = 1; id <= config.patientCount(); id++) {
                if (stopRequested) {
                    throw new InterruptedException("Simulation stopped.");
                }
                Patient patient = new Patient(id, waitingRoom, state);
                patients.add(patient);
                state.patientArrived(id);
                patient.start();
                if (id < config.patientCount()) {
                    int interval = ThreadLocalRandom.current().nextInt(
                            config.minimumArrivalIntervalMillis(),
                            config.maximumArrivalIntervalMillis() + 1);
                    Thread.sleep(interval);
                }
            }
            for (Patient patient : patients) {
                patient.join();
            }
            if (stopRequested) {
                throw new InterruptedException("Simulation stopped.");
            }

            waitingRoom.closeFor(config.doctorCount());
            for (Doctor doctor : doctors) {
                doctor.join();
            }
            state.completed();
            completed = true;
        } catch (InterruptedException exception) {
            interrupted = true;
            stopWorkersAndJoin();
        } catch (RuntimeException exception) {
            state.failed(exception);
            stopWorkersAndJoin();
            throw exception;
        } finally {
            if (!completed) {
                state.stopped();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void stopWorkersAndJoin() {
        stopRequested = true;
        for (Patient patient : patients) {
            patient.interrupt();
        }
        for (Doctor doctor : doctors) {
            doctor.interrupt();
        }
        boolean interrupted = false;
        for (Patient patient : patients) {
            try {
                patient.join();
            } catch (InterruptedException exception) {
                interrupted = true;
                patient.interrupt();
            }
        }
        for (Doctor doctor : doctors) {
            try {
                doctor.join();
            } catch (InterruptedException exception) {
                interrupted = true;
                doctor.interrupt();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
