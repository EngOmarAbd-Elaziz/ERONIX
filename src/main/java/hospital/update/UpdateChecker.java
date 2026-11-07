package hospital.update;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {
    private static final String RELEASE_API =
            "https://api.github.com/repos/EngOmarAbd-Elaziz/ERONIX/releases/latest";
    private static final String RELEASES_PAGE =
            "https://github.com/EngOmarAbd-Elaziz/ERONIX/releases/latest";
    private static final Pattern TAG_NAME =
            Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private UpdateChecker() {
    }

    public static Result checkLatest(String currentVersion) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(RELEASE_API).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "ERONIX-updater");
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub returned HTTP " + status + ".");
            }
            String body = new String(connection.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            Matcher matcher = TAG_NAME.matcher(body);
            if (!matcher.find()) {
                throw new IOException("The latest release does not contain a version tag.");
            }
            String latestVersion = matcher.group(1).replaceFirst("^[vV]", "");
            boolean available = compareVersions(latestVersion, currentVersion) > 0;
            return new Result(latestVersion, RELEASES_PAGE, available);
        } finally {
            connection.disconnect();
        }
    }

    public static int compareVersions(String first, String second) {
        int[] firstParts = parseVersion(first);
        int[] secondParts = parseVersion(second);
        for (int i = 0; i < Math.max(firstParts.length, secondParts.length); i++) {
            int firstPart = i < firstParts.length ? firstParts[i] : 0;
            int secondPart = i < secondParts.length ? secondParts[i] : 0;
            int comparison = Integer.compare(firstPart, secondPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int[] parseVersion(String version) {
        String normalized = version.replaceFirst("^[vV]", "");
        if (!normalized.matches("\\d+(\\.\\d+)*")) {
            throw new IllegalArgumentException("Invalid numeric version: " + version);
        }
        String[] parts = normalized.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return numbers;
    }

    public record Result(String latestVersion, String releaseUrl, boolean updateAvailable) {
    }
}
