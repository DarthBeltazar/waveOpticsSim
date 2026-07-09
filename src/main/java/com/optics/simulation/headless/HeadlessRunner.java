package com.optics.simulation.headless;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optics.simulation.AngularSpectrumPropagator;
import com.optics.simulation.ComplexField;
import com.optics.simulation.analysis.BeamAnalyzer;
import com.optics.simulation.config.SimulationConfig;
import com.optics.simulation.engine.SimulationEngine;
import com.optics.simulation.factory.ElementFactory;
import com.optics.simulation.manager.ElementManager;
import com.optics.simulation.model.OpticalElement;
import com.optics.simulation.util.Colormap;
import com.optics.simulation.util.ImageUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class HeadlessRunner {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java HeadlessRunner <config.json> [output_dir]");
            System.exit(1);
        }
        String configPath = args[0];
        String outputDir = (args.length > 1) ? args[1] : ".";

        try {
            // Создаём выходную директорию, если её нет
            File outDir = new File(outputDir);
            if (!outDir.exists()) {
                if (!outDir.mkdirs()) {
                    System.err.println("Cannot create output directory: " + outputDir);
                    System.exit(1);
                }
            }

            File configFile = new File(configPath);
            if (!configFile.exists()) {
                System.err.println("Config file not found: " + configPath);
                System.exit(1);
            }

            // Загружаем конфигурацию
            ObjectMapper mapper = new ObjectMapper();
            SimulationConfig config = mapper.readValue(configFile, SimulationConfig.class);

            // Создаём элемент менеджер и заполняем
            ElementManager manager = new ElementManager();
            for (SimulationConfig.ElementConfig ec : config.elements) {
                OpticalElement elem = createElement(ec);
                if (elem != null) manager.add(elem);
            }

            // Параметры симуляции
            double lambda = config.lambda;
            double dx = config.dx;
            int N = config.n;
            double beamWidth = config.beamWidth;
            boolean pointSource = "Point source".equals(config.sourceType);
            boolean antiAliasing = config.antiAliasing;
            AngularSpectrumPropagator.setAntiAliasingEnabled(antiAliasing);

            // Создаём поле
            ComplexField field = createCoherentField(N, dx, lambda, beamWidth, pointSource);

            // Применяем элементы
            SimulationEngine engine = new SimulationEngine();
            for (OpticalElement e : manager.getElements()) {
                engine.applySingle(field, e);
            }

            // Сохраняем результаты
            String baseName = new File(configPath).getName();
            baseName = baseName.replaceFirst("[.][^.]+$", ""); // убираем расширение

            // Интенсивность и фаза (PNG)
            Colormap cmap = Colormap.fromDisplayName(config.colormap);
            if (cmap == null) cmap = Colormap.GRAYSCALE;
            boolean logScale = config.logScale;

            ImageUtils.saveColorImage(field.computeIntensity(), cmap, logScale,
                    outputDir + File.separator + baseName + "_intensity.png");
            ImageUtils.saveColorImage(field.computePhase(), cmap, false,
                    outputDir + File.separator + baseName + "_phase.png");

            // Профиль по горизонтали (CSV)
            double[][] intensity = field.computeIntensity();
            int centerRow = N / 2;
            double half = N / 2.0;
            try (PrintWriter pw = new PrintWriter(new FileWriter(outputDir + File.separator + baseName + "_profile.csv"))) {
                pw.println("x_m,Intensity");
                for (int j = 0; j < N; j++) {
                    double x = (j - half) * dx;
                    pw.printf("%.6e,%.6e%n", x, intensity[centerRow][j]);
                }
            }

            // Параметры пучка (текстовый файл)
            BeamAnalyzer analyzer = new BeamAnalyzer(field);
            try (PrintWriter pw = new PrintWriter(new FileWriter(outputDir + File.separator + baseName + "_analysis.txt"))) {
                pw.println(analyzer.toString());
            }

            System.out.println("Headless simulation completed. Results saved in " + outputDir);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static OpticalElement createElement(SimulationConfig.ElementConfig ec) {
        switch (ec.type) {
            case "Free space":
                if (ec.distance != null) return ElementFactory.createFreeSpace(ec.distance);
                break;
            case "Lens":
                if (ec.focalLength != null) return ElementFactory.createLens(ec.focalLength);
                break;
            case "Mirror":
                if (ec.flat != null && ec.flat) return ElementFactory.createMirror(true);
                else if (ec.focalLength != null) return ElementFactory.createMirror(false, ec.focalLength);
                break;
            case "Grating":
                if (ec.period != null && ec.amplitude != null) {
                    boolean rect = ec.rectangular != null && ec.rectangular;
                    return ElementFactory.createGrating(ec.period, ec.amplitude, rect);
                }
                break;
            case "Slit":
                if (ec.width != null) return ElementFactory.createSlit(ec.width);
                break;
        }
        return null;
    }

    private static ComplexField createCoherentField(int N, double dx, double lambda, double beamWidth, boolean pointSource) {
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
                    double r2 = x * x + y * y;
                    double amp = Math.exp(-r2 / (w0 * w0));
                    double r = Math.sqrt(r2);
                    data[i][idx] = amp * Math.cos(k * r);
                    data[i][idx + 1] = amp * Math.sin(k * r);
                } else {
                    double r2 = x * x + y * y;
                    data[i][idx] = Math.exp(-r2 / (beamWidth * beamWidth));
                    data[i][idx + 1] = 0.0;
                }
            }
        }
        return field;
    }
}