package com.optics.simulation.config;

import java.util.ArrayList;
import java.util.List;

public class PresetConfigs {

    public static SimulationConfig freeSpace() {
        SimulationConfig config = new SimulationConfig();
        config.lambda = 0.5e-6;
        config.dx = 1e-5;
        config.n = 512;
        config.beamWidth = 1e-3;
        config.sourceType = "Collimated beam";
        config.antiAliasing = true;
        config.colormap = "Viridis";
        config.logScale = true;

        List<SimulationConfig.ElementConfig> elements = new ArrayList<>();
        SimulationConfig.ElementConfig free = new SimulationConfig.ElementConfig();
        free.type = "Free space";
        free.distance = 0.1;
        elements.add(free);
        config.elements = elements;
        return config;
    }

    public static SimulationConfig lensFocusing() {
        SimulationConfig config = new SimulationConfig();
        config.lambda = 0.5e-6;
        config.dx = 1e-5;
        config.n = 512;
        config.beamWidth = 1e-3;
        config.sourceType = "Collimated beam";
        config.antiAliasing = true;
        config.colormap = "Viridis";
        config.logScale = true;

        List<SimulationConfig.ElementConfig> elements = new ArrayList<>();
        SimulationConfig.ElementConfig lens = new SimulationConfig.ElementConfig();
        lens.type = "Lens";
        lens.focalLength = 0.05;
        elements.add(lens);

        SimulationConfig.ElementConfig free = new SimulationConfig.ElementConfig();
        free.type = "Free space";
        free.distance = 0.05;
        elements.add(free);

        config.elements = elements;
        return config;
    }

    public static SimulationConfig slitDiffraction() {
        SimulationConfig config = new SimulationConfig();
        config.lambda = 0.5e-6;
        config.dx = 1e-5;
        config.n = 1024;
        config.beamWidth = 1e-3;
        config.sourceType = "Collimated beam";
        config.antiAliasing = true;
        config.colormap = "Viridis";
        config.logScale = true;

        List<SimulationConfig.ElementConfig> elements = new ArrayList<>();
        SimulationConfig.ElementConfig slit = new SimulationConfig.ElementConfig();
        slit.type = "Slit";
        slit.width = 0.0001;
        elements.add(slit);

        SimulationConfig.ElementConfig free = new SimulationConfig.ElementConfig();
        free.type = "Free space";
        free.distance = 0.1;
        elements.add(free);

        config.elements = elements;
        return config;
    }

    public static SimulationConfig gratingDiffraction() {
        SimulationConfig config = new SimulationConfig();
        config.lambda = 0.5e-6;
        config.dx = 1e-5;
        config.n = 1024;
        config.beamWidth = 1e-3;
        config.sourceType = "Collimated beam";
        config.antiAliasing = true;
        config.colormap = "Viridis";
        config.logScale = true;

        List<SimulationConfig.ElementConfig> elements = new ArrayList<>();
        SimulationConfig.ElementConfig grating = new SimulationConfig.ElementConfig();
        grating.type = "Grating";
        grating.period = 0.001;
        grating.amplitude = 1.0;
        grating.rectangular = false;
        elements.add(grating);

        SimulationConfig.ElementConfig free = new SimulationConfig.ElementConfig();
        free.type = "Free space";
        free.distance = 0.1;
        elements.add(free);

        config.elements = elements;
        return config;
    }

    public static SimulationConfig lensAndGrating() {
        SimulationConfig config = new SimulationConfig();
        config.lambda = 0.5e-6;
        config.dx = 1e-5;
        config.n = 1024;
        config.beamWidth = 1e-3;
        config.sourceType = "Collimated beam";
        config.antiAliasing = true;
        config.colormap = "Viridis";
        config.logScale = true;

        List<SimulationConfig.ElementConfig> elements = new ArrayList<>();
        SimulationConfig.ElementConfig lens = new SimulationConfig.ElementConfig();
        lens.type = "Lens";
        lens.focalLength = 0.05;
        elements.add(lens);

        SimulationConfig.ElementConfig grating = new SimulationConfig.ElementConfig();
        grating.type = "Grating";
        grating.period = 0.001;
        grating.amplitude = 1.0;
        grating.rectangular = false;
        elements.add(grating);

        SimulationConfig.ElementConfig free = new SimulationConfig.ElementConfig();
        free.type = "Free space";
        free.distance = 0.1;
        elements.add(free);

        config.elements = elements;
        return config;
    }
}