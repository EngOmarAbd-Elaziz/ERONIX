package hospital.simulation;

public final class SimulationController {
    private final HospitalSimulation simulation;

    public SimulationController(SimulationConfig config, SimulationObserver observer) {
        simulation = new HospitalSimulation(config);
        simulation.addObserver(observer);
    }

    public void start() {
        simulation.start();
    }

    public void setConsoleEnabled(boolean enabled) {
        simulation.setConsoleEnabled(enabled);
    }

    public void stop() {
        simulation.stop();
    }

    public SimulationSnapshot snapshot() {
        return simulation.snapshot();
    }

    public void awaitCompletion() throws InterruptedException {
        simulation.awaitCompletion();
    }
}
