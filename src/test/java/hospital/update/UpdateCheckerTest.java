package hospital.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateCheckerTest {
    @Test
    void comparesVersionsNumerically() {
        assertEquals(1, UpdateChecker.compareVersions("1.10.0", "1.9.9"));
        assertEquals(0, UpdateChecker.compareVersions("v2.0", "2.0.0"));
        assertEquals(-1, UpdateChecker.compareVersions("1.2.3", "1.2.4"));
    }

    @Test
    void rejectsNonNumericVersions() {
        assertThrows(IllegalArgumentException.class,
                () -> UpdateChecker.compareVersions("latest", "1.0.0"));
    }
}
