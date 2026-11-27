package hospital.simulation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class WordReportExporter {
    private static final DateTimeFormatter REPORT_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss z")
                    .withZone(ZoneId.systemDefault());

    private WordReportExporter() {
    }

    public static byte[] toDocx(SimulationReport report, Instant generatedAt) throws IOException {
        if (report == null || generatedAt == null) {
            throw new IllegalArgumentException("A simulation report and generation time are required.");
        }

        byte[] outcomesChart = renderOutcomesChartPng(report);
        byte[] histogramChart = renderHistogramChartPng(report);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(zip, "[Content_Types].xml", contentTypes());
            addEntry(zip, "_rels/.rels", packageRelationships());
            addEntry(zip, "word/document.xml", document(report, generatedAt));
            addEntry(zip, "word/_rels/document.xml.rels", documentRelationships());
            addEntry(zip, "word/styles.xml", styles());
            addEntry(zip, "word/settings.xml", settings());
            addBinaryEntry(zip, "word/media/outcomes.png", outcomesChart);
            addBinaryEntry(zip, "word/media/histogram.png", histogramChart);
        }
        return output.toByteArray();
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + "<Override PartName=\"/word/document.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "<Override PartName=\"/word/settings.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml\"/>"
                + "</Types>";
    }

    private static String packageRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
                + "Target=\"word/document.xml\"/>"
                + "</Relationships>";
    }

    private static String documentRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" "
                + "Target=\"styles.xml\"/>"
                + "<Relationship Id=\"rId2\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings\" "
                + "Target=\"settings.xml\"/>"
                + "<Relationship Id=\"rId3\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                + "Target=\"media/outcomes.png\"/>"
                + "<Relationship Id=\"rId4\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                + "Target=\"media/histogram.png\"/>"
                + "</Relationships>";
    }

    private static String settings() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:settings xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:defaultTabStop w:val=\"720\"/>"
                + "</w:settings>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:docDefaults><w:rPrDefault><w:rPr>"
                + "<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/>"
                + "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/>"
                + "</w:rPr></w:rPrDefault></w:docDefaults>"
                + "</w:styles>";
    }

    private static String document(SimulationReport report, Instant generatedAt) {
        StringBuilder xml = new StringBuilder(20_000);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" ")
                .append("xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" ")
                .append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" ")
                .append("xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                .append("<w:body>");

        // ── Cover / title ─────────────────────────────────────────────────────
        centeredParagraph(xml, "ERONIX Simulation Report", 40, "3157D5", true, 0, 80);
        centeredParagraph(xml, "Emergency Room Simulation Summary", 22, "52647C", false, 0, 60);
        horizontalRule(xml, "3157D5", 12);
        centeredParagraph(xml, "Generated: " + REPORT_TIME.format(generatedAt), 18, "52647C", false, 80, 60);
        centeredParagraph(xml, "Status: " + report.status().name(), 20, statusColor(report), true, 0, 200);

        // ── Executive summary ─────────────────────────────────────────────────
        heading(xml, "Executive summary");
        paragraph(xml, report.narrative(), 20, "17243A", false, 0, 120);

        infoBoxRow(xml, new String[]{
                "Total Patients", "Completed", "Completion Rate", "Avg Treatment", "Simulation Time"
        }, new String[]{
                Integer.toString(report.totalPatients()),
                report.completedPatients() + " / " + report.totalPatients(),
                percent(report.completedPatients(), report.totalPatients()),
                formatSeconds(report.averageTreatmentSeconds()),
                formatSeconds(report.simulationSeconds())
        });

        // ── Patient outcomes ──────────────────────────────────────────────────
        heading(xml, "Patient outcomes");
        paragraph(xml, "Distribution of all " + report.totalPatients()
                + " patients across treatment stages:", 20, "52647C", false, 0, 80);

        int maxGroup = report.patientGroups().stream()
                .mapToInt(SimulationReport.PatientGroup::count).max().orElse(1);
        tableWithHeader(xml, List.of("Outcome", "Patients", "Share", "Visual"),
                report.patientGroups().stream()
                        .map(g -> List.of(
                                g.label(),
                                Integer.toString(g.count()),
                                percent(g.count(), report.totalPatients()),
                                asciiBar(g.count(), maxGroup, 20)))
                        .toList());

        embeddedImage(xml, "rId3", "Patient outcomes chart", 5486400L, 2514600L);

        // ── Key statistics ────────────────────────────────────────────────────
        heading(xml, "Key statistics");
        twoColTable(xml, List.of(
                List.of("Total patients", Integer.toString(report.totalPatients())),
                List.of("Patients completed", Integer.toString(report.completedPatients())),
                List.of("Patients in treatment", Integer.toString(report.patientsBeingTreated())),
                List.of("Patients waiting", Integer.toString(report.patientsWaiting())),
                List.of("Patients outside", Integer.toString(report.patientsOutside())),
                List.of("Completion rate", percent(report.completedPatients(), report.totalPatients())),
                List.of("Average treatment duration", formatSeconds(report.averageTreatmentSeconds())),
                List.of("Completed treatments recorded", Integer.toString(report.completedTreatments())),
                List.of("Total simulation duration", formatSeconds(report.simulationSeconds())),
                List.of("Events recorded", Integer.toString(report.eventCount())),
                List.of("Warnings", Integer.toString(report.warnings()))
        ));

        // ── Treatment duration histogram ───────────────────────────────────────
        heading(xml, "Treatment duration histogram");
        paragraph(xml, "Number of completed treatments grouped by duration range:", 20, "52647C", false, 0, 80);

        int maxBin = report.treatmentHistogram().stream()
                .mapToInt(SimulationReport.DurationBin::count).max().orElse(1);
        tableWithHeader(xml, List.of("Duration", "Treatments", "% of Total", "Chart"),
                report.treatmentHistogram().stream()
                        .map(b -> List.of(
                                b.label(),
                                Integer.toString(b.count()),
                                percent(b.count(), Math.max(1, report.completedTreatments())),
                                asciiBar(b.count(), maxBin, 20)))
                        .toList());

        embeddedImage(xml, "rId4", "Treatment duration histogram chart", 5486400L, 2514600L);

        // ── Simulation configuration ───────────────────────────────────────────
        heading(xml, "Simulation configuration");
        SimulationConfig config = report.config();
        twoColTable(xml, List.of(
                List.of("Waiting room capacity", Integer.toString(config.waitingRoomCapacity())),
                List.of("Number of doctors", Integer.toString(config.doctorCount())),
                List.of("Total patients", Integer.toString(config.patientCount())),
                List.of("Treatment rooms", Integer.toString(config.treatmentRoomCount())),
                List.of("Arrival interval",
                        config.minimumArrivalIntervalMillis() + "–"
                                + config.maximumArrivalIntervalMillis() + " ms"),
                List.of("Treatment duration",
                        config.minimumTreatmentMillis() + "–"
                                + config.maximumTreatmentMillis() + " ms")
        ));

        // ── Footer ────────────────────────────────────────────────────────────
        horizontalRule(xml, "D9E1EF", 6);
        centeredParagraph(xml, "This report summarizes the ERONIX educational simulation; it is not a clinical record.",
                16, "52647C", false, 60, 0);

        xml.append("<w:sectPr>")
                .append("<w:pgSz w:w=\"12240\" w:h=\"15840\"/>")
                .append("<w:pgMar w:top=\"1080\" w:right=\"1080\" w:bottom=\"1080\" w:left=\"1080\"/>")
                .append("</w:sectPr>")
                .append("</w:body></w:document>");
        return xml.toString();
    }

    // ── Embedded chart image ──────────────────────────────────────────────────

    private static void embeddedImage(StringBuilder xml, String relId, String name,
            long cx, long cy) {
        int id = relId.hashCode() & 0x7FFF;
        xml.append("<w:p><w:pPr><w:jc w:val=\"center\"/><w:spacing w:before=\"80\" w:after=\"160\"/></w:pPr>")
                .append("<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
                .append("<wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
                .append("<wp:docPr id=\"").append(id).append("\" name=\"").append(escape(name)).append("\"/>")
                .append("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
                .append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                .append("<pic:pic>")
                .append("<pic:nvPicPr><pic:cNvPr id=\"").append(id).append("\" name=\"").append(escape(name))
                .append("\"/><pic:cNvPicPr/></pic:nvPicPr>")
                .append("<pic:blipFill><a:blip r:embed=\"").append(relId).append("\"/>")
                .append("<a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
                .append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx)
                .append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
                .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>")
                .append("</pic:pic></a:graphicData></a:graphic>")
                .append("</wp:inline></w:drawing></w:r></w:p>");
    }

    // ── Pure-Java PNG Chart Renderer ──────────────────────────────────────────

    private static byte[] renderOutcomesChartPng(SimulationReport report) throws IOException {
        int w = 720, h = 330;
        int[] px = new int[w * h];
        fillRect(px, w, h, 0, 0, w, h, 0xFFFFFFFF);
        drawBorderRect(px, w, h, 0, 0, w - 1, h - 1, 0xFFD9E1EF);

        drawString(px, w, h, 24, 18, "PATIENT OUTCOMES", 0xFF3157D5, 2);
        List<SimulationReport.PatientGroup> groups = report.patientGroups();
        int max = Math.max(1, groups.stream().mapToInt(SimulationReport.PatientGroup::count).max().orElse(1));
        int[] palette = {0xFF247044, 0xFF087F78, 0xFF3157D5, 0xFF9A5A08, 0xFF7356BD, 0xFF65768D};

        int rowY = 58;
        int rowStep = 42;
        int barLeft = 170;
        int barMaxW = 460;

        for (int i = 0; i < groups.size(); i++) {
            SimulationReport.PatientGroup g = groups.get(i);
            drawString(px, w, h, 24, rowY + 6, g.label().toUpperCase(), 0xFF17243A, 2);
            fillRect(px, w, h, barLeft, rowY, barMaxW, 24, 0xFFEEF2FF);
            int bw = g.count() == 0 ? 0 : Math.max(6, (int) Math.round(barMaxW * (double) g.count() / max));
            if (bw > 0) {
                fillRect(px, w, h, barLeft, rowY, bw, 24, palette[i % palette.length]);
            }
            drawString(px, w, h, barLeft + barMaxW + 14, rowY + 6,
                    Integer.toString(g.count()), 0xFF17243A, 2);
            rowY += rowStep;
        }
        return encodePng(w, h, px);
    }

    private static byte[] renderHistogramChartPng(SimulationReport report) throws IOException {
        int w = 720, h = 330;
        int[] px = new int[w * h];
        fillRect(px, w, h, 0, 0, w, h, 0xFFFFFFFF);
        drawBorderRect(px, w, h, 0, 0, w - 1, h - 1, 0xFFD9E1EF);

        drawString(px, w, h, 24, 18, "TREATMENT DURATION HISTOGRAM", 0xFF087F78, 2);
        List<SimulationReport.DurationBin> bins = report.treatmentHistogram();
        int max = Math.max(1, bins.stream().mapToInt(SimulationReport.DurationBin::count).max().orElse(1));

        int plotLeft = 56, plotRight = w - 36, plotTop = 64, plotBottom = h - 54;
        int plotH = plotBottom - plotTop;

        for (int i = 0; i <= 4; i++) {
            int y = plotBottom - (plotH * i / 4);
            fillRect(px, w, h, plotLeft, y, plotRight - plotLeft, 1, 0xFFD9E1EF);
        }

        int slot = (plotRight - plotLeft) / Math.max(1, bins.size());
        int barW = Math.max(24, (int) (slot * 0.52));
        for (int i = 0; i < bins.size(); i++) {
            SimulationReport.DurationBin bin = bins.get(i);
            int bh = bin.count() == 0 ? 0 : Math.max(4, (int) Math.round(plotH * (double) bin.count() / max));
            int x = plotLeft + slot * i + (slot - barW) / 2;
            if (bh > 0) {
                fillRect(px, w, h, x, plotBottom - bh, barW, bh, 0xFF087F78);
            }
            drawString(px, w, h, x + barW / 2 - 6, plotBottom - bh - 18,
                    Integer.toString(bin.count()), 0xFF17243A, 2);
            String label = bin.label().replace("–", "-").replace(" s", "S").toUpperCase();
            drawString(px, w, h, x + 2, plotBottom + 14, label, 0xFF52647C, 2);
        }
        return encodePng(w, h, px);
    }

    private static void fillRect(int[] px, int w, int h, int x, int y, int rw, int rh, int argb) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y);
        int x1 = Math.min(w, x + rw), y1 = Math.min(h, y + rh);
        for (int yy = y0; yy < y1; yy++) {
            int row = yy * w;
            for (int xx = x0; xx < x1; xx++) {
                px[row + xx] = argb;
            }
        }
    }

    private static void drawBorderRect(int[] px, int w, int h, int x0, int y0, int x1, int y1, int argb) {
        fillRect(px, w, h, x0, y0, x1 - x0 + 1, 1, argb);
        fillRect(px, w, h, x0, y1, x1 - x0 + 1, 1, argb);
        fillRect(px, w, h, x0, y0, 1, y1 - y0 + 1, argb);
        fillRect(px, w, h, x1, y0, 1, y1 - y0 + 1, argb);
    }

    /** Minimal 5x7 bitmap font for crisp chart labels without AWT or Android Canvas. */
    private static void drawString(int[] px, int w, int h, int x, int y, String text, int argb, int scale) {
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            byte[] glyph = glyph5x7(text.charAt(i));
            for (int row = 0; row < 7; row++) {
                int bits = glyph[row];
                for (int col = 0; col < 5; col++) {
                    if ((bits & (1 << (4 - col))) != 0) {
                        fillRect(px, w, h, cx + col * scale, y + row * scale, scale, scale, argb);
                    }
                }
            }
            cx += 6 * scale;
        }
    }

    private static byte[] glyph5x7(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'A' -> new byte[]{0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11};
            case 'B' -> new byte[]{0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E};
            case 'C' -> new byte[]{0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E};
            case 'D' -> new byte[]{0x1E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1E};
            case 'E' -> new byte[]{0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F};
            case 'F' -> new byte[]{0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10};
            case 'G' -> new byte[]{0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0F};
            case 'H' -> new byte[]{0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11};
            case 'I' -> new byte[]{0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E};
            case 'K' -> new byte[]{0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11};
            case 'L' -> new byte[]{0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F};
            case 'M' -> new byte[]{0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11};
            case 'N' -> new byte[]{0x11, 0x19, 0x15, 0x13, 0x11, 0x11, 0x11};
            case 'O' -> new byte[]{0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E};
            case 'P' -> new byte[]{0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10};
            case 'R' -> new byte[]{0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11};
            case 'S' -> new byte[]{0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E};
            case 'T' -> new byte[]{0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04};
            case 'U' -> new byte[]{0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E};
            case 'V' -> new byte[]{0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04};
            case 'W' -> new byte[]{0x11, 0x11, 0x11, 0x15, 0x15, 0x1B, 0x11};
            case '0' -> new byte[]{0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E};
            case '1' -> new byte[]{0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E};
            case '2' -> new byte[]{0x0E, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1F};
            case '3' -> new byte[]{0x1E, 0x01, 0x01, 0x0E, 0x01, 0x01, 0x1E};
            case '4' -> new byte[]{0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02};
            case '5' -> new byte[]{0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E};
            case '6' -> new byte[]{0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E};
            case '7' -> new byte[]{0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08};
            case '8' -> new byte[]{0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E};
            case '9' -> new byte[]{0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C};
            case '-' -> new byte[]{0x00, 0x00, 0x00, 0x1F, 0x00, 0x00, 0x00};
            case '+' -> new byte[]{0x00, 0x04, 0x04, 0x1F, 0x04, 0x04, 0x00};
            case '<' -> new byte[]{0x02, 0x04, 0x08, 0x10, 0x08, 0x04, 0x02};
            default -> new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        };
    }

    private static byte[] encodePng(int w, int h, int[] argb) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(h * (1 + w * 4));
        for (int y = 0; y < h; y++) {
            raw.write(0); // filter type 0
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int c = argb[row + x];
                raw.write((c >> 16) & 0xFF);
                raw.write((c >> 8) & 0xFF);
                raw.write(c & 0xFF);
                raw.write((c >> 24) & 0xFF);
            }
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream def = new DeflaterOutputStream(compressed)) {
            def.write(raw.toByteArray());
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream(13);
        writeInt(ihdr, w);
        writeInt(ihdr, h);
        ihdr.write(8); // bit depth
        ihdr.write(6); // RGBA
        ihdr.write(0);
        ihdr.write(0);
        ihdr.write(0);
        writeChunk(png, "IHDR", ihdr.toByteArray());
        writeChunk(png, "IDAT", compressed.toByteArray());
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private static void heading(StringBuilder xml, String text) {
        xml.append("<w:p><w:pPr>")
                .append("<w:spacing w:before=\"240\" w:after=\"80\"/>")
                .append("<w:jc w:val=\"left\"/>")
                .append("</w:pPr><w:r>")
                .append("<w:rPr>")
                .append("<w:b/><w:color w:val=\"3157D5\"/>")
                .append("<w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/>")
                .append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>")
                .append("</w:rPr>")
                .append("<w:t xml:space=\"preserve\">").append(escape(text))
                .append("</w:t></w:r></w:p>");
        xml.append("<w:p><w:pPr>")
                .append("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"3157D5\"/></w:pBdr>")
                .append("<w:spacing w:after=\"120\"/>")
                .append("</w:pPr></w:p>");
    }

    private static void paragraph(StringBuilder xml, String text, int halfPt,
            String color, boolean bold, int spaceBefore, int spaceAfter) {
        xml.append("<w:p><w:pPr>")
                .append("<w:spacing w:before=\"").append(spaceBefore)
                .append("\" w:after=\"").append(spaceAfter).append("\"/>")
                .append("</w:pPr><w:r><w:rPr>")
                .append(bold ? "<w:b/>" : "")
                .append("<w:color w:val=\"").append(color).append("\"/>")
                .append("<w:sz w:val=\"").append(halfPt).append("\"/>")
                .append("<w:szCs w:val=\"").append(halfPt).append("\"/>")
                .append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>")
                .append("</w:rPr>")
                .append("<w:t xml:space=\"preserve\">").append(escape(text))
                .append("</w:t></w:r></w:p>");
    }

    private static void centeredParagraph(StringBuilder xml, String text, int halfPt,
            String color, boolean bold, int spaceBefore, int spaceAfter) {
        xml.append("<w:p><w:pPr>")
                .append("<w:jc w:val=\"center\"/>")
                .append("<w:spacing w:before=\"").append(spaceBefore)
                .append("\" w:after=\"").append(spaceAfter).append("\"/>")
                .append("</w:pPr><w:r><w:rPr>")
                .append(bold ? "<w:b/>" : "")
                .append("<w:color w:val=\"").append(color).append("\"/>")
                .append("<w:sz w:val=\"").append(halfPt).append("\"/>")
                .append("<w:szCs w:val=\"").append(halfPt).append("\"/>")
                .append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>")
                .append("</w:rPr>")
                .append("<w:t xml:space=\"preserve\">").append(escape(text))
                .append("</w:t></w:r></w:p>");
    }

    private static void horizontalRule(StringBuilder xml, String color, int sz) {
        xml.append("<w:p><w:pPr>")
                .append("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"").append(sz)
                .append("\" w:space=\"1\" w:color=\"").append(color).append("\"/></w:pBdr>")
                .append("<w:spacing w:before=\"80\" w:after=\"80\"/>")
                .append("</w:pPr></w:p>");
    }

    private static void infoBoxRow(StringBuilder xml, String[] labels, String[] values) {
        int cols = labels.length;
        xml.append("<w:tbl><w:tblPr>")
                .append("<w:tblW w:w=\"0\" w:type=\"auto\"/>")
                .append("<w:tblBorders>")
                .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D9E1EF\"/>")
                .append("</w:tblBorders>")
                .append("<w:tblCellSpacing w:w=\"0\" w:type=\"dxa\"/>")
                .append("<w:tblCellMar>")
                .append("<w:top w:w=\"100\" w:type=\"dxa\"/>")
                .append("<w:left w:w=\"160\" w:type=\"dxa\"/>")
                .append("<w:bottom w:w=\"100\" w:type=\"dxa\"/>")
                .append("<w:right w:w=\"160\" w:type=\"dxa\"/>")
                .append("</w:tblCellMar>")
                .append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EEF2FF\"/>")
                .append("</w:tblPr>");
        xml.append("<w:tr>");
        for (String label : labels) {
            xml.append("<w:tc><w:tcPr>")
                    .append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EEF2FF\"/>")
                    .append("</w:tcPr>")
                    .append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r>")
                    .append("<w:rPr><w:color w:val=\"52647C\"/>")
                    .append("<w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/>")
                    .append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/></w:rPr>")
                    .append("<w:t xml:space=\"preserve\">").append(escape(label)).append("</w:t>")
                    .append("</w:r></w:p></w:tc>");
        }
        xml.append("</w:tr>");
        xml.append("<w:tr>");
        for (int i = 0; i < cols; i++) {
            xml.append("<w:tc><w:tcPr>")
                    .append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EEF2FF\"/>")
                    .append("</w:tcPr>")
                    .append("<w:p><w:pPr><w:jc w:val=\"center\"/>")
                    .append("<w:spacing w:after=\"80\"/></w:pPr><w:r>")
                    .append("<w:rPr><w:b/><w:color w:val=\"3157D5\"/>")
                    .append("<w:sz w:val=\"32\"/><w:szCs w:val=\"32\"/>")
                    .append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/></w:rPr>")
                    .append("<w:t xml:space=\"preserve\">").append(escape(values[i])).append("</w:t>")
                    .append("</w:r></w:p></w:tc>");
        }
        xml.append("</w:tr></w:tbl>");
        xml.append("<w:p><w:pPr><w:spacing w:after=\"160\"/></w:pPr></w:p>");
    }

    private static void tableWithHeader(StringBuilder xml,
            List<String> header, List<List<String>> rows) {
        xml.append("<w:tbl><w:tblPr>")
                .append("<w:tblW w:w=\"0\" w:type=\"auto\"/>")
                .append("<w:tblBorders>");
        for (String edge : List.of("top", "left", "bottom", "right", "insideH", "insideV")) {
            xml.append("<w:").append(edge)
                    .append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D9E1EF\"/>");
        }
        xml.append("</w:tblBorders></w:tblPr>");

        xml.append("<w:tr>");
        for (String h : header) {
            xml.append("<w:tc><w:tcPr>")
                    .append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"3157D5\"/>")
                    .append("<w:tcMar><w:top w:w=\"80\" w:type=\"dxa\"/>")
                    .append("<w:left w:w=\"120\" w:type=\"dxa\"/>")
                    .append("<w:bottom w:w=\"80\" w:type=\"dxa\"/>")
                    .append("<w:right w:w=\"120\" w:type=\"dxa\"/></w:tcMar>")
                    .append("</w:tcPr>")
                    .append("<w:p><w:pPr><w:jc w:val=\"left\"/></w:pPr><w:r>")
                    .append("<w:rPr><w:b/><w:color w:val=\"FFFFFF\"/>")
                    .append("<w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/>")
                    .append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/></w:rPr>")
                    .append("<w:t xml:space=\"preserve\">").append(escape(h)).append("</w:t>")
                    .append("</w:r></w:p></w:tc>");
        }
        xml.append("</w:tr>");

        for (int r = 0; r < rows.size(); r++) {
            String fill = (r % 2 == 0) ? "F7F9FF" : "FFFFFF";
            xml.append("<w:tr>");
            for (String cell : rows.get(r)) {
                boolean isChart = cell.startsWith("█") || cell.startsWith("▓")
                        || cell.startsWith("░") || cell.equals("—");
                xml.append("<w:tc><w:tcPr>")
                        .append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(fill).append("\"/>")
                        .append("<w:tcMar><w:top w:w=\"80\" w:type=\"dxa\"/>")
                        .append("<w:left w:w=\"120\" w:type=\"dxa\"/>")
                        .append("<w:bottom w:w=\"80\" w:type=\"dxa\"/>")
                        .append("<w:right w:w=\"120\" w:type=\"dxa\"/></w:tcMar>")
                        .append("</w:tcPr>")
                        .append("<w:p><w:r>")
                        .append("<w:rPr>")
                        .append(isChart ? "<w:color w:val=\"3157D5\"/>" : "<w:color w:val=\"17243A\"/>")
                        .append("<w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/>")
                        .append(isChart
                                ? "<w:rFonts w:ascii=\"Courier New\" w:hAnsi=\"Courier New\"/>"
                                : "<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>")
                        .append("</w:rPr>")
                        .append("<w:t xml:space=\"preserve\">").append(escape(cell)).append("</w:t>")
                        .append("</w:r></w:p></w:tc>");
            }
            xml.append("</w:tr>");
        }
        xml.append("</w:tbl>");
        xml.append("<w:p><w:pPr><w:spacing w:after=\"160\"/></w:pPr></w:p>");
    }

    private static void twoColTable(StringBuilder xml, List<List<String>> rows) {
        xml.append("<w:tbl><w:tblPr>")
                .append("<w:tblW w:w=\"0\" w:type=\"auto\"/>")
                .append("<w:tblBorders>");
        for (String edge : List.of("top", "left", "bottom", "right", "insideH", "insideV")) {
            xml.append("<w:").append(edge)
                    .append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D9E1EF\"/>");
        }
        xml.append("</w:tblBorders></w:tblPr>");
        for (int r = 0; r < rows.size(); r++) {
            String fill = (r % 2 == 0) ? "F7F9FF" : "FFFFFF";
            xml.append("<w:tr>");
            List<String> row = rows.get(r);
            xml.append("<w:tc><w:tcPr>")
                    .append("<w:tcW w:w=\"3600\" w:type=\"dxa\"/>")
                    .append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(fill).append("\"/>")
                    .append("<w:tcMar><w:top w:w=\"80\" w:type=\"dxa\"/>")
                    .append("<w:left w:w=\"120\" w:type=\"dxa\"/>")
                    .append("<w:bottom w:w=\"80\" w:type=\"dxa\"/>")
                    .append("<w:right w:w=\"120\" w:type=\"dxa\"/></w:tcMar>")
                    .append("</w:tcPr><w:p><w:r>")
                    .append("<w:rPr><w:color w:val=\"52647C\"/>")
                    .append("<w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/>")
                    .append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/></w:rPr>")
                    .append("<w:t xml:space=\"preserve\">").append(escape(row.get(0))).append("</w:t>")
                    .append("</w:r></w:p></w:tc>");
            xml.append("<w:tc><w:tcPr>")
                    .append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(fill).append("\"/>")
                    .append("<w:tcMar><w:top w:w=\"80\" w:type=\"dxa\"/>")
                    .append("<w:left w:w=\"120\" w:type=\"dxa\"/>")
                    .append("<w:bottom w:w=\"80\" w:type=\"dxa\"/>")
                    .append("<w:right w:w=\"120\" w:type=\"dxa\"/></w:tcMar>")
                    .append("</w:tcPr><w:p><w:r>")
                    .append("<w:rPr><w:b/><w:color w:val=\"17243A\"/>")
                    .append("<w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/>")
                    .append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/></w:rPr>")
                    .append("<w:t xml:space=\"preserve\">").append(escape(row.size() > 1 ? row.get(1) : "")).append("</w:t>")
                    .append("</w:r></w:p></w:tc>");
            xml.append("</w:tr>");
        }
        xml.append("</w:tbl>");
        xml.append("<w:p><w:pPr><w:spacing w:after=\"160\"/></w:pPr></w:p>");
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String statusColor(SimulationReport report) {
        return switch (report.status()) {
            case COMPLETED -> "087F78";
            case STOPPED -> "9A5A08";
            case RUNNING -> "3157D5";
            case IDLE -> "52647C";
        };
    }

    private static String percent(int value, int total) {
        return total == 0 ? "0.0%" : String.format(java.util.Locale.ROOT, "%.1f%%",
                value * 100.0 / total);
    }

    private static String formatSeconds(double seconds) {
        return String.format(java.util.Locale.ROOT, "%.2f s", seconds);
    }

    private static String asciiBar(int count, int maxCount, int width) {
        if (maxCount == 0 || count == 0) {
            return "—";
        }
        int filled = Math.max(1, (int) Math.round((double) count / maxCount * width));
        return "█".repeat(Math.min(width, filled)) + "░".repeat(Math.max(0, width - filled)) + "  " + count;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '&') {
                escaped.append("&amp;");
            } else if (codePoint == '<') {
                escaped.append("&lt;");
            } else if (codePoint == '>') {
                escaped.append("&gt;");
            } else if (codePoint == '"') {
                escaped.append("&quot;");
            } else if (codePoint == '\'') {
                escaped.append("&apos;");
            } else if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r'
                    || codePoint >= 0x20 && codePoint <= 0xD7FF
                    || codePoint >= 0xE000 && codePoint <= 0xFFFD
                    || codePoint >= 0x10000 && codePoint <= 0x10FFFF) {
                escaped.appendCodePoint(codePoint);
            }
        }
        return escaped.toString();
    }

    private static void addEntry(ZipOutputStream zip, String name, String content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void addBinaryEntry(ZipOutputStream zip, String name, byte[] bytes)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
