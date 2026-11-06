package hospital.simulation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public final class Doctor extends Thread {
    private final int doctorId;
    private final WaitingRoom waitingRoom;
    private final TreatmentRooms treatmentRooms;
    private final SimulationConfig config;
    private final SimulationState state;
    private final CountDownLatch ready;

    Doctor(
            int doctorId,
            WaitingRoom waitingRoom,
            TreatmentRooms treatmentRooms,
            SimulationConfig config,
            SimulationState state,
            CountDownLatch ready) {
        super("Doctor-" + doctorId);
        this.doctorId = doctorId;
        this.waitingRoom = waitingRoom;
        this.treatmentRooms = treatmentRooms;
        this.config = config;
        this.state = state;
        this.ready = ready;
    }

    @Override
    public void run() {
        ready.countDown();
        try {
            while (!isInterrupted()) {
                Patient patient = waitingRoom.take(doctorId);
                if (patient == null) {
                    state.doctorStopped(doctorId);
                    return;
                }
                state.tryingForRoom(doctorId);
                boolean acquired = false;
                boolean treated = false;
                int treatmentMillis = ThreadLocalRandom.current().nextInt(
                        config.minimumTreatmentMillis(), config.maximumTreatmentMillis() + 1);
                try {
                    acquired = treatmentRooms.tryAcquire(
                            TreatmentRooms.ACQUISITION_TIMEOUT_MILLIS);
                    if (!acquired) {
                        state.doctorTimedOut(doctorId, patient.getPatientId());
                        patient.signalSentBack();
                        continue;
                    }
                    treatmentRooms.assign(doctorId, patient.getPatientId(), treatmentMillis);
                    boolean interruptedDuringTreatment = false;
                    try {
                        Thread.sleep(treatmentMillis);
                        state.treatmentFinished(doctorId, patient.getPatientId());
                        treated = true;
                    } catch (InterruptedException exception) {
                        state.treatmentInterrupted(doctorId, patient.getPatientId());
                        interruptedDuringTreatment = true;
                    } finally {
                        treatmentRooms.release(
                                doctorId, patient.getPatientId(), treated);
                        acquired = false;
                        state.doctorAvailable(doctorId);
                    }
                    if (treated) {
                        patient.signalTreated();
                    } else {
                        patient.signalSentBack();
                    }
                    if (interruptedDuringTreatment) {
                        interrupt();
                        return;
                    }
                } finally {
                    if (acquired) {
                        treatmentRooms.release(
                                doctorId, patient.getPatientId(), false);
                        state.doctorAvailable(doctorId);
                        patient.signalSentBack();
                    }
                }
            }
        } catch (InterruptedException exception) {
            interrupt();
        }
    }
}
