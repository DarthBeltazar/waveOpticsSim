package com.optics.simulation.benchmark;

import com.optics.simulation.ComplexField;
import com.optics.simulation.engine.SimulationEngine;
import com.optics.simulation.factory.ElementFactory;
import com.optics.simulation.manager.ElementManager;
import com.optics.simulation.model.OpticalElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Performance benchmark for the optical simulation.
 * Measures time spent on field creation, each element application, and total propagation.
 */
public class Benchmark {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASUREMENT_ITERATIONS = 5;

    public static void main(String[] args) {
        // Default parameters
        int N = 1024;
        double dx = 1e-5;
        double lambda = 0.5e-6;
        double beamWidth = 1e-3;
        boolean pointSource = false;

        // Parse command-line arguments: java Benchmark [N] [dx] [lambda] [beamWidth]
        if (args.length > 0) N = Integer.parseInt(args[0]);
        if (args.length > 1) dx = Double.parseDouble(args[1]);
        if (args.length > 2) lambda = Double.parseDouble(args[2]);
        if (args.length > 3) beamWidth = Double.parseDouble(args[3]);

        System.out.println("=== Optical Simulation Benchmark ===");
        System.out.printf("N=%d, dx=%.2e, lambda=%.2e, beamWidth=%.2e%n", N, dx, lambda, beamWidth);
        System.out.println("Building complex optical scheme...");

        // Build a complex optical scheme: Source -> Lens -> Free space -> Grating -> Free space -> Slit -> Free space -> Mirror -> Free space
        ElementManager manager = new ElementManager();
        manager.add(ElementFactory.createLens(0.05));              // Lens, f=50 mm
        manager.add(ElementFactory.createFreeSpace(0.1));          // propagate 10 cm
        manager.add(ElementFactory.createGrating(0.001, 1.0, false)); // sinusoidal grating
        manager.add(ElementFactory.createFreeSpace(0.05));         // propagate 5 cm
        manager.add(ElementFactory.createSlit(0.0005));            // slit width 0.5 mm
        manager.add(ElementFactory.createFreeSpace(0.1));          // propagate 10 cm
        manager.add(ElementFactory.createMirror(false, 0.05));     // curved mirror f=50 mm
        manager.add(ElementFactory.createFreeSpace(0.1));          // propagate 10 cm

        List<OpticalElement> elements = new ArrayList<>(manager.getElements());
        System.out.println("Number of elements: " + elements.size());

        // Warm-up
        System.out.println("\nWarming up (" + WARMUP_ITERATIONS + " runs)...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runSimulation(N, dx, lambda, beamWidth, pointSource, elements, true);
        }

        // Measurement
        System.out.println("\nMeasuring (" + MEASUREMENT_ITERATIONS + " runs)...");
        double totalTime = 0;
        double[] stageTimes = new double[elements.size() + 1]; // +1 for field creation
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            double[] times = runSimulation(N, dx, lambda, beamWidth, pointSource, elements, false);
            long end = System.nanoTime();
            totalTime += (end - start) / 1e9;
            for (int i = 0; i < times.length; i++) {
                stageTimes[i] += times[i];
            }
        }

        // Average times
        for (int i = 0; i < stageTimes.length; i++) {
            stageTimes[i] /= MEASUREMENT_ITERATIONS;
        }
        totalTime /= MEASUREMENT_ITERATIONS;

        // Print results
        System.out.println("\n=== Results (average over " + MEASUREMENT_ITERATIONS + " runs) ===");
        System.out.printf("Total simulation time: %.3f s%n", totalTime);
        System.out.printf("Throughput: %.2f operations/sec%n", 1.0 / totalTime);
        System.out.println("\nBreakdown by stages:");
        System.out.printf("  %-25s %10s %10s%n", "Stage", "Time (s)", "Percent");
        System.out.println("  " + "-".repeat(50));
        System.out.printf("  %-25s %10.4f %9.1f%%%n", "Field creation", stageTimes[0], stageTimes[0] / totalTime * 100);
        for (int i = 0; i < elements.size(); i++) {
            String name = elements.get(i).getDescription().substring(0, Math.min(25, elements.get(i).getDescription().length()));
            System.out.printf("  %-25s %10.4f %9.1f%%%n", name, stageTimes[i + 1], stageTimes[i + 1] / totalTime * 100);
        }
        System.out.println();
    }

    /**
     * Runs a single simulation and returns the time spent in each stage.
     *
     * @param N           grid size
     * @param dx          sampling step
     * @param lambda      wavelength
     * @param beamWidth   initial beam width
     * @param pointSource true for point source, false for collimated
     * @param elements    list of optical elements
     * @param silent      if true, suppress output (for warmup)
     * @return array of times in seconds: [fieldCreation, element1, element2, ...]
     */
    private static double[] runSimulation(int N, double dx, double lambda, double beamWidth,
                                          boolean pointSource, List<OpticalElement> elements,
                                          boolean silent) {
        double[] times = new double[elements.size() + 1];
        int idx = 0;

        // 1. Create field
        long start = System.nanoTime();
        ComplexField field = createField(N, dx, lambda, beamWidth, pointSource);
        long end = System.nanoTime();
        times[idx++] = (end - start) / 1e9;

        // 2. Apply elements
        SimulationEngine engine = new SimulationEngine();
        for (int i = 0; i < elements.size(); i++) {
            start = System.nanoTime();
            engine.applySingle(field, elements.get(i)); // need to add this method to SimulationEngine
            end = System.nanoTime();
            times[idx++] = (end - start) / 1e9;
        }

        return times;
    }

    private static ComplexField createField(int N, double dx, double lambda, double beamWidth, boolean pointSource) {
        ComplexField field = new ComplexField(N, dx, lambda);
        double half = N / 2.0;
        double k = 2.0 * Math.PI / lambda;
        for (int i = 0; i < N; i++) {
            double x = (i - half) * dx;
            for (int j = 0; j < N; j++) {
                double y = (j - half) * dx;
                if (pointSource) {
                    double w0 = 1e-6;
                    double r2 = x * x + y * y;
                    double amp = Math.exp(-r2 / (w0 * w0));
                    double r = Math.sqrt(r2);
                    field.setValue(i, j, amp * Math.cos(k * r), amp * Math.sin(k * r));
                } else {
                    double r2 = x * x + y * y;
                    field.setValue(i, j, Math.exp(-r2 / (beamWidth * beamWidth)), 0.0);
                }
            }
        }
        return field;
    }
}