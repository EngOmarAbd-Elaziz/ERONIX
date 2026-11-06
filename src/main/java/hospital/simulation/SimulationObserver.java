package hospital.simulation;

@FunctionalInterface
public interface SimulationObserver {
    void onUpdate(SimulationSnapshot snapshot, SimulationEvent event);
}
