package com.optics.simulation.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationConfig {
    public double lambda;
    public double dx;
    public int n;
    public double beamWidth;
    public String sourceType; // "Collimated beam" or "Point source"
    public boolean antiAliasing;
    public String colormap;   // e.g. "Viridis"
    public boolean logScale;
    public List<ElementConfig> elements;

    public static class ElementConfig {
        public String type;          // "Free space", "Lens", "Mirror", "Grating", "Slit"
        public Double distance;
        public Double focalLength;
        public Double period;
        public Double amplitude;
        public Boolean flat;
        public Boolean rectangular;
        public Double width;

        public ElementConfig() {
        }
    }
}