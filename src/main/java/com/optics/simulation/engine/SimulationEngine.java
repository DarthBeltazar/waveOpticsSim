package com.optics.simulation.engine;

import com.optics.simulation.ComplexField;
import com.optics.simulation.model.OpticalElement;

import java.util.List;

public class SimulationEngine {

    public void run(ComplexField field, List<? extends OpticalElement> elements) {
        for (OpticalElement e : elements) {
            e.apply(field);
        }
    }

    public void applySingle(ComplexField field, OpticalElement element) {
        element.apply(field);
    }
}