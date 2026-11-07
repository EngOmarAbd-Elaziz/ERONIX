package hospital.simulation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SimulationReport(
        SimulationConfig config,
        SimulationSnapshot.Status status,
        int totalPatients,
        int completedPatients,
        int patientsBeingTreated,
        int patientsWaiting,
        int patientsOutside,
        int patientsAssigned,
        int patientsNotArrived,
        int warnings,
        int eventCount,
        int completedTreatments,
        double averageTreatmentSeconds,
        double simulationSeconds,
        List<PatientGroup> patientGroups,
        List<DurationBin> treatmentHistogram) {

    private static final Pattern STARTED_TREATMENT =
            Pattern.compile("begins treating (P\\d+)\\.");
    private static final Pattern FINISHED_TREATMENT =
            Pattern.compile("finished treating (P\\d+)\\.");

    public SimulationReport {
        patientGroups = List.copyOf(patientGroups);
        treatmentHistogram = List.copyOf(treatmentHistogram);
    }

    public static SimulationReport from(
            SimulationSnapshot snapshot, List<SimulationEvent> events) {
        if (snapshot == null || events == null) {
            throw new IllegalArgumentException("A simulation snapshot and event list are required.");
        }

        int total = snapshot.config().patientCount();
        int completed = Math.min(total, snapshot.patientsCompleted());
        int treating = Math.min(total - completed, snapshot.patientsBeingTreated());
        int waiting = Math.min(total - completed - treating, snapshot.patientsWaiting());
        int outside = Math.min(total - completed - treating - waiting, snapshot.patientsOutside());
        int arrived = (int) events.stream()
                .filter(event -> event.category() == SimulationSnapshot.EventCategory.PATIENT
                        && event.message().endsWith(" arrived."))
                .count();
        arrived = Math.min(total, arrived);
        int assigned = Math.min(
                Math.max(0, arrived - completed - treating - waiting - outside),
                total - completed - treating - waiting - outside);
        int notArrived = Math.max(0, total - completed - treating - waiting - outside - assigned);

        List<Long> treatmentDurations = treatmentDurations(events);
        double averageTreatmentSeconds = treatmentDurations.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0) / 1_000.0;
        double simulationSeconds = simulationDurationSeconds(events);
        List<PatientGroup> groups = List.of(
                new PatientGroup("Completed", completed),
                new PatientGroup("In treatment", treating),
                new PatientGroup("Waiting", waiting),
                new PatientGroup("Outside", outside),
                new PatientGroup("Assigned", assigned),
                new PatientGroup("Not arrived", notArrived));
        List<DurationBin> histogram = histogram(treatmentDurations);
        int warnings = (int) events.stream()
                .filter(event -> event.category() == SimulationSnapshot.EventCategory.WARNING)
                .count();

        return new SimulationReport(snapshot.config(), snapshot.status(), total, completed,
                treating, waiting, outside, assigned, notArrived, warnings, events.size(),
                treatmentDurations.size(), averageTreatmentSeconds, simulationSeconds,
                groups, histogram);
    }

    private static List<Long> treatmentDurations(List<SimulationEvent> events) {
        Map<String, Instant> startedAt = new LinkedHashMap<>();
        List<Long> durations = new ArrayList<>();
        for (SimulationEvent event : events) {
            Matcher started = STARTED_TREATMENT.matcher(event.message());
            if (started.find()) {
                startedAt.put(started.group(1), event.timestamp());
                continue;
            }
            Matcher finished = FINISHED_TREATMENT.matcher(event.message());
            if (finished.find()) {
                Instant start = startedAt.remove(finished.group(1));
                if (start != null && !event.timestamp().isBefore(start)) {
                    durations.add(Duration.between(start, event.timestamp()).toMillis());
                }
            }
        }
        return durations;
    }

    private static double simulationDurationSeconds(List<SimulationEvent> events) {
        Instant first = events.stream()
                .filter(event -> event.message().startsWith("Simulation configuration:"))
                .map(SimulationEvent::timestamp)
                .findFirst()
                .orElse(null);
        Instant last = null;
        for (SimulationEvent event : events) {
            if (last == null || event.timestamp().isAfter(last)) {
                last = event.timestamp();
            }
        }
        if (first == null || last == null) {
            return 0;
        }
        return Duration.between(first, last).toMillis() / 1_000.0;
    }

    private static List<DurationBin> histogram(List<Long> durations) {
        String[] labels = {"1–<2 s", "2–<3 s", "3–<4 s", "4–<5 s", "5+ s"};
        int[] counts = new int[labels.length];
        for (long duration : durations) {
            int bin = (int) (duration / 1_000) - 1;
            counts[Math.min(counts.length - 1, Math.max(0, bin))]++;
        }
        List<DurationBin> bins = new ArrayList<>(labels.length);
        for (int i = 0; i < labels.length; i++) {
            bins.add(new DurationBin(labels[i], counts[i]));
        }
        return bins;
    }

    public double completionPercent() {
        return totalPatients == 0 ? 0 : completedPatients * 100.0 / totalPatients;
    }

    public String narrative() {
        return switch (status) {
            case COMPLETED -> String.format(java.util.Locale.ROOT,
                    "The simulation completed with %d of %d patients treated.",
                    completedPatients, totalPatients);
            case STOPPED -> String.format(java.util.Locale.ROOT,
                    "The simulation stopped after treating %d of %d patients.",
                    completedPatients, totalPatients);
            case RUNNING -> String.format(java.util.Locale.ROOT,
                    "%.1f%% complete: %d treated, %d waiting, %d in treatment, and %d outside.",
                    completionPercent(), completedPatients, patientsWaiting,
                    patientsBeingTreated, patientsOutside);
            case IDLE -> "The simulation has not started.";
        };
    }

    public record PatientGroup(String label, int count) {
    }

    public record DurationBin(String label, int count) {
    }
}
