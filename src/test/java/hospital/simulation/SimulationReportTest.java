package hospital.simulation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;

import static hospital.simulation.SimulationSnapshot.EventCategory;
import static hospital.simulation.SimulationSnapshot.Status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationReportTest {
    @Test
    void summarizesPatientStatesAndTreatmentDurations() {
        SimulationConfig config = new SimulationConfig(2, 1, 4, 1, 0, 0, 1_000, 5_000);
        SimulationSnapshot snapshot = new SimulationSnapshot(
                config, Status.RUNNING, List.of("P3"), 1, List.of("P4"), 1, 1,
                List.of(), List.of());
        Instant start = Instant.parse("2026-11-07T10:00:00Z");
        List<SimulationEvent> events = List.of(
                event(1, start, EventCategory.SYSTEM, "Simulation configuration: starting."),
                event(2, start.plusMillis(100), EventCategory.PATIENT, "P1 arrived."),
                event(3, start.plusMillis(200), EventCategory.PATIENT, "P2 arrived."),
                event(4, start.plusMillis(300), EventCategory.PATIENT, "P3 arrived."),
                event(5, start.plusMillis(400), EventCategory.PATIENT, "P4 arrived."),
                event(6, start.plusMillis(500), EventCategory.DOCTOR, "Doctor 1 begins treating P1."),
                event(7, start.plusMillis(2_900), EventCategory.DOCTOR, "Doctor 1 finished treating P1."),
                event(8, start.plusMillis(3_000), EventCategory.WARNING, "A patient is waiting outside."));

        SimulationReport report = SimulationReport.from(snapshot, events);

        assertEquals(4, report.totalPatients());
        assertEquals(1, report.completedPatients());
        assertEquals(1, report.patientsBeingTreated());
        assertEquals(1, report.patientsWaiting());
        assertEquals(1, report.patientsOutside());
        assertEquals(0, report.patientsAssigned());
        assertEquals(0, report.patientsNotArrived());
        assertEquals(25.0, report.completionPercent());
        assertEquals(2.4, report.averageTreatmentSeconds(), 0.001);
        assertEquals(1, report.treatmentHistogram().get(1).count());
        assertEquals(1, report.warnings());
        assertEquals(8, report.eventCount());
    }

    @Test
    void createsReadableDocxWithReportTables() throws Exception {
        SimulationConfig config = new SimulationConfig(2, 1, 2, 1, 0, 0, 1_000, 5_000);
        SimulationSnapshot snapshot = new SimulationSnapshot(
                config, Status.COMPLETED, List.of(), 0, List.of(), 0, 2,
                List.of(), List.of());
        SimulationReport report = SimulationReport.from(snapshot, List.of());
        byte[] docx = WordReportExporter.toDocx(
                report, Instant.parse("2026-11-07T10:00:00Z"));

        List<String> entries = new ArrayList<>();
        String document = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                if (entry.getName().equals("word/document.xml")) {
                    document = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }

        assertTrue(entries.contains("[Content_Types].xml"));
        assertTrue(entries.contains("_rels/.rels"));
        assertTrue(entries.contains("word/media/outcomes.png"));
        assertTrue(entries.contains("word/media/histogram.png"));
        assertTrue(document != null && document.contains("ERONIX Simulation Report"));
        assertTrue(document.contains("Patient outcomes"));
        assertTrue(document.contains("Treatment duration histogram"));
        assertTrue(document.contains("<w:drawing>"));
        assertTrue(document.contains("100.0%"));
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(document.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsMissingReportInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> SimulationReport.from(null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> WordReportExporter.toDocx(null, Instant.now()));
    }

    @Test
    void distinguishesPatientsNotYetArrivedFromAssignedPatients() {
        SimulationConfig config = new SimulationConfig(2, 1, 4, 1, 0, 0, 1_000, 2_000);
        SimulationSnapshot snapshot = new SimulationSnapshot(
                config, Status.RUNNING, List.of("P2"), 0, List.of(),
                0, 1, List.of(), List.of());
        List<SimulationEvent> events = List.of(
                event(1, Instant.parse("2026-11-07T10:00:00Z"),
                        EventCategory.PATIENT, "P1 arrived."),
                event(2, Instant.parse("2026-11-07T10:00:01Z"),
                        EventCategory.PATIENT, "P2 arrived."),
                event(3, Instant.parse("2026-11-07T10:00:02Z"),
                        EventCategory.PATIENT, "P3 arrived."),
                event(4, Instant.parse("2026-11-07T10:00:03Z"),
                        EventCategory.SUCCESS, "P1 left."));

        SimulationReport report = SimulationReport.from(snapshot, events);

        assertEquals(1, report.patientsAssigned());
        assertEquals(1, report.patientsNotArrived());
        assertEquals(4, report.patientGroups().stream()
                .mapToInt(SimulationReport.PatientGroup::count).sum());
    }

    private static SimulationEvent event(
            long sequence, Instant timestamp, EventCategory category, String message) {
        return new SimulationEvent(sequence, timestamp, category, message);
    }
}
