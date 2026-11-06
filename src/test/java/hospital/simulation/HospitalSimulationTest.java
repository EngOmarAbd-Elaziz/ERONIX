package hospital.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static hospital.simulation.SimulationSnapshot.EventCategory;
import static hospital.simulation.SimulationSnapshot.Status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HospitalSimulationTest {
    @Test
    void assignmentSampleConfigurationCompletesWithoutMissingPatients() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(2, 3, 5, 0, 0, 1_000, 1_000);
        HospitalSimulation simulation = new HospitalSimulation(config);
        simulation.setConsoleEnabled(false);
        List<SimulationEvent> events = new CopyOnWriteArrayList<>();
        simulation.addObserver((snapshot, event) -> events.add(event));

        simulation.start();
        assertTrue(await(simulation, 10));

        assertEquals(Status.COMPLETED, simulation.snapshot().status());
        assertEquals(5, simulation.snapshot().patientsCompleted());
        assertEquals(5, events.stream()
                .filter(event -> event.message().matches("P[1-5] left\\."))
                .count());
        assertEquals(events.stream().filter(event ->
                        event.message().contains(" acquired Treatment Room ")).count(),
                events.stream().filter(event ->
                        event.message().contains(" released Treatment Room ")).count());
    }

    @Test
    void configuredTreatmentRoomCountIsUsedThroughoutSimulation() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(3, 3, 3, 3, 0, 0, 1_000, 1_000);
        HospitalSimulation simulation = new HospitalSimulation(config);
        simulation.setConsoleEnabled(false);
        List<ObservedUpdate> updates = new CopyOnWriteArrayList<>();
        simulation.addObserver((snapshot, event) ->
                updates.add(new ObservedUpdate(snapshot, event)));

        assertEquals(3, simulation.snapshot().treatmentRooms().size());

        simulation.start();
        assertTrue(await(simulation, 10));

        assertEquals(Status.COMPLETED, simulation.snapshot().status());
        assertEquals(3, simulation.snapshot().patientsCompleted());
        assertEquals(3, simulation.snapshot().availableTreatmentRooms());
        assertTrue(updates.stream().allMatch(update ->
                update.snapshot().treatmentRooms().size() == 3));
    }

    @Test
    void patientsCompleteAndPublishedSnapshotsRespectResourceLimits() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(1, 2, 4, 0, 0, 1_000, 1_000);
        HospitalSimulation simulation = new HospitalSimulation(config);
        simulation.setConsoleEnabled(false);
        List<ObservedUpdate> updates = new CopyOnWriteArrayList<>();
        simulation.addObserver((snapshot, event) -> updates.add(new ObservedUpdate(snapshot, event)));

        simulation.start();
        assertTrue(await(simulation, 12));

        SimulationSnapshot finalSnapshot = simulation.snapshot();
        assertEquals(Status.COMPLETED, finalSnapshot.status());
        assertEquals(4, finalSnapshot.patientsCompleted());
        assertEquals(0, finalSnapshot.patientsWaiting());
        assertEquals(0, finalSnapshot.patientsOutside());
        assertEquals(2, finalSnapshot.availableTreatmentRooms());
        assertEquals(4, updates.stream()
                .filter(update -> update.event().message().matches("P[1-4] left\\."))
                .count());
        assertTrue(updates.stream().allMatch(update ->
                update.snapshot().patientsWaiting() <= config.waitingRoomCapacity()));
        assertTrue(updates.stream().allMatch(update ->
                update.snapshot().treatmentRooms().size() == 2));
        assertTrue(updates.stream().anyMatch(update ->
                update.event().message().contains("waiting outside")));
        assertEquals(updates.size(), updates.stream()
                .map(update -> update.event().sequence()).distinct().count());
        assertFalse(updates.isEmpty());
    }

    @Test
    void thirdDoctorTimesOutAndPatientIsEventuallyTreated() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(5, 3, 3, 0, 0, 5_000, 5_000);
        HospitalSimulation simulation = new HospitalSimulation(config);
        simulation.setConsoleEnabled(false);
        List<ObservedUpdate> updates = new CopyOnWriteArrayList<>();
        simulation.addObserver((snapshot, event) -> updates.add(new ObservedUpdate(snapshot, event)));

        simulation.start();
        assertTrue(await(simulation, 20));

        List<SimulationEvent> events = updates.stream().map(ObservedUpdate::event).toList();
        assertTrue(events.stream().anyMatch(event ->
                event.message().contains("could not acquire a treatment room")));
        assertTrue(events.stream().anyMatch(event ->
                event.message().contains("is sending P") && event.message().contains("back")));
        assertEquals(3, events.stream()
                .filter(event -> event.category() == EventCategory.SUCCESS
                        && event.message().matches("P[1-3] left\\."))
                .count());
        assertEquals(Status.COMPLETED, simulation.snapshot().status());
        assertEquals(2, simulation.snapshot().availableTreatmentRooms());
        assertTrue(updates.stream().anyMatch(update ->
                update.snapshot().treatmentRooms().stream()
                        .filter(room -> room.status()
                                == SimulationSnapshot.RoomStatus.OCCUPIED)
                        .count() == 2));

        for (SimulationEvent event : events) {
            if (event.message().matches("P[1-3] left\\.")) {
                String patient = event.message().substring(0, event.message().indexOf(' '));
                SimulationEvent finished = events.stream()
                        .filter(candidate -> candidate.sequence() < event.sequence()
                                && candidate.message().endsWith(" finished treating " + patient + "."))
                        .max((first, second) -> Long.compare(
                                first.sequence(), second.sequence()))
                        .orElseThrow();
                String doctor = finished.message().substring(
                        0, finished.message().indexOf(" finished"));
                assertTrue(events.stream().anyMatch(candidate ->
                        candidate.sequence() > finished.sequence()
                                && candidate.sequence() < event.sequence()
                                && candidate.message().startsWith(doctor + " released Treatment Room")));
            }
        }
        assertTrue(events.get(events.size() - 2).message().equals("All patients have been treated."));
        assertTrue(events.get(events.size() - 1).message().equals("Simulation completed."));
    }

    @Test
    void stoppingDuringTreatmentReleasesResourcesAndJoinsWorkers() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(2, 1, 1, 0, 0, 5_000, 5_000);
        HospitalSimulation simulation = new HospitalSimulation(config);
        simulation.setConsoleEnabled(false);
        CountDownLatch treatmentStarted = new CountDownLatch(1);
        simulation.addObserver((snapshot, event) -> {
            if (event.message().contains("begins treating P1")) {
                treatmentStarted.countDown();
            }
        });

        simulation.start();
        assertTrue(treatmentStarted.await(3, TimeUnit.SECONDS));
        simulation.stop();

        assertTrue(await(simulation, 5));
        assertEquals(Status.STOPPED, simulation.snapshot().status());
        assertEquals(2, simulation.snapshot().availableTreatmentRooms());
    }

    private static boolean await(HospitalSimulation simulation, int timeoutSeconds)
            throws InterruptedException {
        Thread waiter = new Thread(() -> {
            try {
                simulation.awaitCompletion();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();
        waiter.join(TimeUnit.SECONDS.toMillis(timeoutSeconds));
        if (waiter.isAlive()) {
            simulation.stop();
            waiter.join(TimeUnit.SECONDS.toMillis(3));
            return false;
        }
        return true;
    }

    private record ObservedUpdate(SimulationSnapshot snapshot, SimulationEvent event) {
    }
}
