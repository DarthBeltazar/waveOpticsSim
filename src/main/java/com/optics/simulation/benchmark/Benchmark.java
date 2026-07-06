package com.optics.simulation.benchmark;

import com.optics.simulation.ComplexField;
import com.optics.simulation.engine.SimulationEngine;
import com.optics.simulation.factory.ElementFactory;
import com.optics.simulation.manager.ElementManager;
import com.optics.simulation.model.OpticalElement;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Performance benchmark for the optical simulation.
 * Measures time spent on field creation, each element application, and total propagation.
 * Includes warm-up and statistical output.
 */
public class Benchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASUREMENT_ITERATIONS = 10;

    public static void main(String[] args) throws IOException {
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

        // Build a complex optical scheme
        ElementManager manager = new ElementManager();
        manager.add(ElementFactory.createLens(0.05));
        manager.add(ElementFactory.createFreeSpace(0.1));
        manager.add(ElementFactory.createGrating(0.001, 1.0, false));
        manager.add(ElementFactory.createFreeSpace(0.05));
        manager.add(ElementFactory.createSlit(0.0005));
        manager.add(ElementFactory.createFreeSpace(0.1));
        manager.add(ElementFactory.createMirror(false, 0.05));
        manager.add(ElementFactory.createFreeSpace(0.1));

        List<OpticalElement> elements = new ArrayList<>(manager.getElements());
        System.out.println("Number of elements: " + elements.size());

        // Warm-up
        System.out.println("\nWarming up (" + WARMUP_ITERATIONS + " runs)...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runSimulation(N, dx, lambda, beamWidth, pointSource, elements, true);
        }

        // Measurement
        System.out.println("\nMeasuring (" + MEASUREMENT_ITERATIONS + " runs)...");
        double[] totalTimes = new double[MEASUREMENT_ITERATIONS];
        double[][] stageTimes = new double[MEASUREMENT_ITERATIONS][elements.size() + 1];

        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            double[] times = runSimulation(N, dx, lambda, beamWidth, pointSource, elements, false);
            long end = System.nanoTime();
            totalTimes[run] = (end - start) / 1e9;
            System.arraycopy(times, 0, stageTimes[run], 0, times.length);
        }

        // Compute averages and standard deviations
        int stageCount = stageTimes[0].length;
        double[] avgStage = new double[stageCount];
        double[] stdStage = new double[stageCount];
        double avgTotal = mean(totalTimes);
        double stdTotal = std(totalTimes);

        for (int s = 0; s < stageCount; s++) {
            double[] vals = new double[MEASUREMENT_ITERATIONS];
            for (int r = 0; r < MEASUREMENT_ITERATIONS; r++) {
                vals[r] = stageTimes[r][s];
            }
            avgStage[s] = mean(vals);
            stdStage[s] = std(vals);
        }

        // Print results
        System.out.println("\n=== Results (average over " + MEASUREMENT_ITERATIONS + " runs) ===");
        System.out.printf("Total time: %.3f ± %.3f s%n", avgTotal, stdTotal);
        System.out.printf("Throughput: %.2f ops/sec%n", 1.0 / avgTotal);
        System.out.println("\nBreakdown by stages:");
        System.out.printf("  %-25s %12s %12s %8s%n", "Stage", "Time (s)", "±", "Percent");
        System.out.println("  " + "-".repeat(65));
        System.out.printf("  %-25s %12.4f %12.4f %7.1f%%%n", "Field creation", avgStage[0], stdStage[0], avgStage[0]/avgTotal*100);
        for (int i = 0; i < elements.size(); i++) {
            String name = elements.get(i).getDescription();
            if (name.length() > 25) name = name.substring(0, 22) + "...";
            System.out.printf("  %-25s %12.4f %12.4f %7.1f%%%n", name, avgStage[i+1], stdStage[i+1], avgStage[i+1]/avgTotal*100);
        }

        // Save to CSV
        try (PrintWriter pw = new PrintWriter(new FileWriter("benchmarkResults/benchmark_results.csv"))) {
            pw.println("Stage,Average (s),StdDev (s),Percent,ms,ms,ms");
            pw.printf("Total,%.4f,%.4f,100.0%n", avgTotal, stdTotal);
            pw.printf("Field creation,%.4f,%.4f,%.1f%n", avgStage[0], stdStage[0], avgStage[0]/avgTotal*100);
            for (int i = 0; i < elements.size(); i++) {
                String name = elements.get(i).getDescription().replaceAll(",", ";");
                pw.printf("%s,%.4f,%.4f,%.1f%n", name, avgStage[i+1], stdStage[i+1], avgStage[i+1]/avgTotal*100);
            }
        }
        System.out.println("\nResults saved to benchmark_results.csv");
    }

    private static double[] runSimulation(int N, double dx, double lambda, double beamWidth,
                                          boolean pointSource, List<OpticalElement> elements,
                                          boolean silent) {
        double[] times = new double[elements.size() + 1];
        int idx = 0;

        // Create field
        long start = System.nanoTime();
        ComplexField field = createField(N, dx, lambda, beamWidth, pointSource);
        long end = System.nanoTime();
        times[idx++] = (end - start) / 1e9;

        // Apply elements
        SimulationEngine engine = new SimulationEngine();
        for (OpticalElement e : elements) {
            start = System.nanoTime();
            engine.applySingle(field, e);
            end = System.nanoTime();
            times[idx++] = (end - start) / 1e9;
        }

        return times;
    }

    private static ComplexField createField(int N, double dx, double lambda, double beamWidth, boolean pointSource) {
        ComplexField field = new ComplexField(N, dx, lambda);
        double half = N / 2.0;
        double k = 2.0 * Math.PI / lambda;
        double[][] data = field.getData();
        for (int i = 0; i < N; i++) {
            double x = (i - half) * dx;
            for (int j = 0; j < N; j++) {
                double y = (j - half) * dx;
                int idx = 2 * j;
                if (pointSource) {
                    double w0 = 1e-6;
                    double r2 = x*x + y*y;
                    double amp = Math.exp(-r2/(w0*w0));
                    double r = Math.sqrt(r2);
                    data[i][idx] = amp * Math.cos(k*r);
                    data[i][idx+1] = amp * Math.sin(k*r);
                } else {
                    double r2 = x*x + y*y;
                    data[i][idx] = Math.exp(-r2/(beamWidth*beamWidth));
                    data[i][idx+1] = 0.0;
                }
            }
        }
        return field;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double std(double[] values) {
        double m = mean(values);
        double sum = 0.0;
        for (double v : values) sum += (v - m) * (v - m);
        return Math.sqrt(sum / (values.length - 1));
    }
}