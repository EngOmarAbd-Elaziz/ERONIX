package hospital.simulation;

public final class Patient extends Thread {
    private final int id;
    private final WaitingRoom waitingRoom;
    private final SimulationState state;
    private boolean outcomeReady;
    private boolean treated;

    public Patient(int id, WaitingRoom waitingRoom) {
        this(id, waitingRoom, waitingRoom.getState());
    }

    Patient(int id, WaitingRoom waitingRoom, SimulationState state) {
        super("Patient-" + id);
        this.id = id;
        this.waitingRoom = waitingRoom;
        this.state = state;
    }

    public int getPatientId() {
        return id;
    }

    public String getPatientName() {
        return "P" + id;
    }

    @Override
    public String toString() {
        return getPatientName();
    }

    @Override
    public void run() {
        try {
            while (true) {
                waitingRoom.enter(this);
                boolean wasTreated = waitUntilOutcome();
                if (wasTreated) {
                    if (state != null) {
                        state.patientLeft(id);
                    }
                    return;
                }
            }
        } catch (InterruptedException exception) {
            interrupt();
        }
    }

    public synchronized boolean waitUntilOutcome() throws InterruptedException {
        while (!outcomeReady) {
            wait();
        }
        boolean result = treated;
        outcomeReady = false;
        return result;
    }

    public synchronized void signalTreated() {
        signal(true);
    }

    public synchronized void signalSentBack() {
        signal(false);
    }

    private void signal(boolean treatmentSucceeded) {
        if (outcomeReady) {
            throw new IllegalStateException(getPatientName() + " received duplicate outcomes.");
        }
        treated = treatmentSucceeded;
        outcomeReady = true;
        notifyAll();
    }
}
