package hospital.android;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipInputStream;

import hospital.simulation.SimulationConfig;
import hospital.simulation.SimulationEvent;
import hospital.simulation.SimulationReport;
import hospital.simulation.SimulationSnapshot;
import hospital.simulation.WordReportExporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimulationReportTest {
    @Test
    public void reportsCompletedPatientsAndExportsDocx() throws Exception {
        SimulationConfig config = new SimulationConfig(2, 1, 2, 1, 0, 0, 1_000, 2_000);
        SimulationSnapshot snapshot = new SimulationSnapshot(
                config, SimulationSnapshot.Status.COMPLETED, List.of(),
                0, List.of(), 0, 2, List.of(), List.of());
        SimulationEvent complete = new SimulationEvent(1, Instant.now(),
                SimulationSnapshot.EventCategory.SUCCESS, "Simulation completed.");

        SimulationReport report = SimulationReport.from(snapshot, List.of(complete));
        byte[] document = WordReportExporter.toDocx(report, Instant.now());

        assertEquals(100.0, report.completionPercent(), 0.001);
        assertTrue(report.narrative().contains("2 of 2"));
        boolean hasDocument = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(document))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("word/document.xml")) {
                    hasDocument = true;
                    break;
                }
            }
        }
        assertTrue(hasDocument);
    }
}
