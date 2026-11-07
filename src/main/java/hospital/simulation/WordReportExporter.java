package hospital.simulation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class WordReportExporter {
    private WordReportExporter() {
    }

    public static byte[] toDocx(SimulationReport report, Instant generatedAt) throws IOException {
        if (report == null || generatedAt == null) {
            throw new IllegalArgumentException("A simulation report and generation time are required.");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(zip, "[Content_Types].xml", contentTypes());
            addEntry(zip, "_rels/.rels", packageRelationships());
            addEntry(zip, "word/document.xml", document(report, generatedAt));
        }
        return output.toByteArray();
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
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

    private static String document(SimulationReport report, Instant generatedAt) {
        StringBuilder xml = new StringBuilder(8_000);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">")
                .append("<w:body>");
        paragraph(xml, "ERONIX Simulation Report", "Title");
        paragraph(xml, "Generated: " + generatedAt, "Subtitle");
        paragraph(xml, "Simulation status: " + report.status(), "Heading1");
        paragraph(xml, report.narrative(), "Normal");
        paragraph(xml, "Patient outcomes", "Heading1");
        table(xml, rowsWithHeader(List.of("Outcome", "Patients", "Share", "Visual"),
                report.patientGroups().stream()
                        .map(group -> List.of(group.label(), Integer.toString(group.count()),
                                percent(group.count(), report.totalPatients()),
                                bar(group.count(), report.totalPatients())))
                        .toList()));

        paragraph(xml, "Key statistics", "Heading1");
        table(xml, List.of(
                List.of("Metric", "Value"),
                List.of("Total patients", Integer.toString(report.totalPatients())),
                List.of("Completed", Integer.toString(report.completedPatients())),
                List.of("Completion rate", percent(report.completedPatients(), report.totalPatients())),
                List.of("Average treatment duration", formatSeconds(report.averageTreatmentSeconds())),
                List.of("Simulation duration", formatSeconds(report.simulationSeconds())),
                List.of("Events recorded", Integer.toString(report.eventCount())),
                List.of("Warnings", Integer.toString(report.warnings()))));

        paragraph(xml, "Treatment duration histogram", "Heading1");
        table(xml, rowsWithHeader(List.of("Duration", "Treatments", "Chart"),
                report.treatmentHistogram().stream()
                        .map(bin -> List.of(bin.label(), Integer.toString(bin.count()),
                                bar(bin.count(), report.completedTreatments())))
                        .toList()));

        paragraph(xml, "Simulation configuration", "Heading1");
        SimulationConfig config = report.config();
        table(xml, List.of(
                List.of("Setting", "Value"),
                List.of("Waiting room capacity", Integer.toString(config.waitingRoomCapacity())),
                List.of("Doctors", Integer.toString(config.doctorCount())),
                List.of("Patients", Integer.toString(config.patientCount())),
                List.of("Treatment rooms", Integer.toString(config.treatmentRoomCount())),
                List.of("Arrival interval", config.minimumArrivalIntervalMillis() + "–"
                        + config.maximumArrivalIntervalMillis() + " ms"),
                List.of("Treatment duration", config.minimumTreatmentMillis() + "–"
                        + config.maximumTreatmentMillis() + " ms")));
        paragraph(xml, "This report summarizes the ERONIX educational simulation; it is not a clinical record.",
                "Caption");
        xml.append("<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/>"
                + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/>"
                + "</w:sectPr></w:body></w:document>");
        return xml.toString();
    }

    private static void table(StringBuilder xml, List<List<String>> rows) {
        xml.append("<w:tbl><w:tblPr><w:tblBorders>");
        for (String edge : List.of("top", "left", "bottom", "right", "insideH", "insideV")) {
            xml.append("<w:").append(edge)
                    .append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D9E1EF\"/>");
        }
        xml.append("</w:tblBorders></w:tblPr>");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            xml.append("<w:tr>");
            for (String value : rows.get(rowIndex)) {
                xml.append("<w:tc><w:tcPr><w:tcMar><w:top w:w=\"90\" w:type=\"dxa\"/>"
                        + "<w:left w:w=\"120\" w:type=\"dxa\"/>"
                        + "<w:bottom w:w=\"90\" w:type=\"dxa\"/>"
                        + "<w:right w:w=\"120\" w:type=\"dxa\"/></w:tcMar></w:tcPr>");
                xml.append("<w:p><w:r>");
                if (rowIndex == 0) {
                    xml.append("<w:rPr><w:b/></w:rPr>");
                }
                xml.append("<w:t xml:space=\"preserve\">").append(escape(value))
                        .append("</w:t></w:r></w:p></w:tc>");
            }
            xml.append("</w:tr>");
        }
        xml.append("</w:tbl>");
    }

    private static List<List<String>> rowsWithHeader(
            List<String> header, List<List<String>> body) {
        List<List<String>> rows = new java.util.ArrayList<>(body.size() + 1);
        rows.add(header);
        rows.addAll(body);
        return rows;
    }

    private static void paragraph(StringBuilder xml, String text, String style) {
        xml.append("<w:p><w:pPr><w:spacing w:after=\"")
                .append(style.equals("Heading1") ? "120" : "80")
                .append("\"/></w:pPr><w:r>");
        if (style.equals("Title")) {
            xml.append("<w:rPr><w:b/><w:color w:val=\"3157D5\"/><w:sz w:val=\"34\"/></w:rPr>");
        } else if (style.equals("Heading1")) {
            xml.append("<w:rPr><w:b/><w:color w:val=\"17243A\"/><w:sz w:val=\"26\"/></w:rPr>");
        } else if (style.equals("Subtitle") || style.equals("Caption")) {
            xml.append("<w:rPr><w:color w:val=\"65768D\"/><w:sz w:val=\"19\"/></w:rPr>");
        }
        xml.append("<w:t xml:space=\"preserve\">")
                .append(escape(text)).append("</w:t></w:r></w:p>");
    }

    private static String percent(int value, int total) {
        return total == 0 ? "0.0%" : String.format(java.util.Locale.ROOT, "%.1f%%",
                value * 100.0 / total);
    }

    private static String formatSeconds(double seconds) {
        return String.format(java.util.Locale.ROOT, "%.2f s", seconds);
    }

    private static String bar(int count, int total) {
        if (count == 0 || total == 0) {
            return "—";
        }
        int blocks = Math.max(1, (int) Math.round(count * 20.0 / total));
        return "■".repeat(Math.min(20, blocks));
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
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
}
