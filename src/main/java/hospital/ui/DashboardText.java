package hospital.ui;

import hospital.simulation.SimulationEvent;
import hospital.simulation.SimulationSnapshot;

import java.util.Map;

final class DashboardText {
    private static final Map<String, String> TEXT = Map.ofEntries(
            Map.entry("app.title", "ERONIX"),
            Map.entry("app.subtitle", "Emergency Room Simulation"),
            Map.entry("app.icon", "ERONIX application icon"),
            Map.entry("theme.light", "Switch to light theme"),
            Map.entry("theme.dark", "Switch to dark theme"),
            Map.entry("fullscreen.exit", "⛶  Exit full screen"),
            Map.entry("fullscreen.enter", "⛶  Full screen"),
            Map.entry("fullscreen.tooltip", "Toggle full screen (F11)"),
            Map.entry("theme.tooltip", "Switch between light and dark themes"),
            Map.entry("overview.title", "Operations overview"),
            Map.entry("overview.subtitle", "Monitor patient flow, clinical capacity, and live activity."),
            Map.entry("completion", "✓  All patients have been treated"),
            Map.entry("stat.total", "Total patients"),
            Map.entry("stat.waiting", "Patients waiting"),
            Map.entry("stat.outside", "Patients outside"),
            Map.entry("stat.treating", "Being treated"),
            Map.entry("stat.completed", "Completed"),
            Map.entry("stat.doctors.available", "Doctors available"),
            Map.entry("stat.doctors.busy", "Doctors busy"),
            Map.entry("stat.rooms.available", "Rooms available"),
            Map.entry("waiting.title", "Waiting room"),
            Map.entry("waiting.subtitle", "Capacity and patient flow"),
            Map.entry("waiting.seats", "Occupied seats"),
            Map.entry("waiting.outside", "Waiting outside"),
            Map.entry("waiting.none", "No patients waiting outside"),
            Map.entry("seat.available", "Available"),
            Map.entry("doctors.title", "Doctors"),
            Map.entry("doctors.subtitle", "Live availability and treatment progress"),
            Map.entry("doctors.empty", "Doctor cards will appear when the simulation starts."),
            Map.entry("doctor.no.patient", "No patient assigned"),
            Map.entry("doctor.prefix", "Doctor"),
            Map.entry("doctor.current.patient", "Current patient"),
            Map.entry("doctor.idle", "Idle"),
            Map.entry("doctor.waiting.room", "Waiting for room"),
            Map.entry("doctor.treating", "Treating"),
            Map.entry("doctor.available", "Available"),
            Map.entry("doctor.stopped", "Stopped"),
            Map.entry("rooms.title", "Treatment rooms"),
            Map.entry("rooms.subtitle", "Shared treatment resources"),
            Map.entry("room.title", "Treatment room"),
            Map.entry("room.available", "Available"),
            Map.entry("room.occupied", "Occupied"),
            Map.entry("room.ready", "Ready for the next patient"),
            Map.entry("events.title", "Live event timeline"),
            Map.entry("events.subtitle", "Simulation events in chronological order"),
            Map.entry("config.title", "Simulation setup"),
            Map.entry("config.subtitle", "Choose patient, room, and timing settings before starting"),
            Map.entry("field.capacity", "Waiting seats"),
            Map.entry("field.doctors", "Doctors"),
            Map.entry("field.patients", "Patients"),
            Map.entry("field.rooms", "Treatment rooms"),
            Map.entry("field.arrival.min", "Min arrival interval (seconds)"),
            Map.entry("field.arrival.max", "Max arrival interval (seconds)"),
            Map.entry("field.treatment.min", "Min treatment (seconds)"),
            Map.entry("field.treatment.max", "Max treatment (seconds)"),
            Map.entry("button.start", "Start simulation"),
            Map.entry("button.stop", "Stop simulation"),
            Map.entry("button.reset", "Reset"),
            Map.entry("config.hint", "Assignment-compatible treatment duration: 1–5 seconds."),
            Map.entry("alert.title", "Invalid settings"),
            Map.entry("alert.header", "Please check your entries"),
            Map.entry("validation.integer", "%s must be a whole number."),
            Map.entry("validation.range", "%s must be between %s and %s."),
            Map.entry("validation.seconds", "%s must be a number of seconds with up to 3 decimal places."),
            Map.entry("validation.order", "Minimum must be less than or equal to maximum."),
            Map.entry("status.idle", "IDLE"),
            Map.entry("status.running", "RUNNING"),
            Map.entry("status.completed", "COMPLETED"),
            Map.entry("status.stopped", "STOPPED"),
            Map.entry("status.stopping", "STOPPING"),
            Map.entry("event.system", "SYSTEM"),
            Map.entry("event.patient", "PATIENT"),
            Map.entry("event.waiting", "WAITING"),
            Map.entry("event.doctor", "DOCTOR"),
            Map.entry("event.room", "ROOM"),
            Map.entry("event.warning", "NOTICE"),
            Map.entry("event.success", "SUCCESS"));

    private DashboardText() {
    }

    static String text(String key) {
        String value = TEXT.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing dashboard text: " + key);
        }
        return value;
    }

    static String category(SimulationSnapshot.EventCategory category) {
        return text("event." + switch (category) {
            case SYSTEM -> "system";
            case PATIENT -> "patient";
            case WAITING_ROOM -> "waiting";
            case DOCTOR -> "doctor";
            case TREATMENT_ROOM -> "room";
            case WARNING -> "warning";
            case SUCCESS -> "success";
        });
    }

    static String status(SimulationSnapshot.Status status) {
        return text("status." + status.name().toLowerCase());
    }

    static String doctorStatus(SimulationSnapshot.DoctorStatus status) {
        String key = switch (status) {
            case IDLE -> "doctor.idle";
            case WAITING_FOR_ROOM -> "doctor.waiting.room";
            case TREATING -> "doctor.treating";
            case AVAILABLE -> "doctor.available";
            case STOPPED -> "doctor.stopped";
        };
        return text(key);
    }

    static String eventMessage(SimulationEvent event) {
        return event.message();
    }
}
