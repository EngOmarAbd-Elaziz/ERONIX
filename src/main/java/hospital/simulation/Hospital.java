package hospital.simulation;

import java.util.Scanner;

public final class Hospital {
    private Hospital() {
    }

    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);
        int capacity = readPositiveInt(scanner, "Waiting room capacity: ", 1_000);
        int doctorCount = readPositiveInt(scanner, "Number of doctors: ", 100);
        int patientCount = readPositiveInt(scanner, "Number of patients: ", 10_000);
        HospitalSimulation simulation = new HospitalSimulation(
                SimulationConfig.defaults(capacity, doctorCount, patientCount));
        simulation.start();
        simulation.awaitCompletion();
    }

    private static int readPositiveInt(Scanner scanner, String prompt, int maximum) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value >= 1 && value <= maximum) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Ask again when input is not a positive whole number.
            }
            System.out.println("Please enter a whole number between 1 and " + maximum + ".");
        }
    }
}
