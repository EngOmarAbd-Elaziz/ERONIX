package hospital.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreatmentRoomsTest {
    @Test
    void exposesExactlyTwoPermitsAndReturnsReleasedRoom() throws InterruptedException {
        TreatmentRooms rooms = new TreatmentRooms();

        assertTrue(rooms.tryAcquire(0));
        assertTrue(rooms.tryAcquire(0));
        assertFalse(rooms.tryAcquire(40));

        rooms.release();
        assertTrue(rooms.tryAcquire(0));
        rooms.release();
        rooms.release();
        assertTrue(rooms.tryAcquire(0));
        assertTrue(rooms.tryAcquire(0));
        assertFalse(rooms.tryAcquire(0));
    }
}
