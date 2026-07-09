package com.optics.simulation.engine;

import com.optics.simulation.ComplexField;
import com.optics.simulation.model.OpticalElement;

import java.util.ArrayList;
import java.util.List;

public class SimulationEngine {
    private boolean debugMode = false;
    private final List<ComplexField> snapshots = new ArrayList<>();

    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    public List<ComplexField> getSnapshots() {
        return snapshots;
    }

    public void run(ComplexField field, List<OpticalElement> elements) {
        snapshots.clear();
        if (debugMode) snapshots.add(new ComplexField(field));
        for (OpticalElement e : elements) {
            e.apply(field);
            if (debugMode) snapshots.add(new ComplexField(field));
        }
    }

    /**
     * Applies a single element to the field.
     * Used for benchmarking to measure per-element time.
     */
    public void applySingle(ComplexField field, OpticalElement element) {
        element.apply(field);
    }
}