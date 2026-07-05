package com.optics.simulation.model;

import com.optics.simulation.AngularSpectrumPropagator;
import com.optics.simulation.ComplexField;
import javafx.scene.canvas.GraphicsContext;

public class FreeSpaceElement implements OpticalElement {
    private final double distance;

    public FreeSpaceElement(double distance) {
        this.distance = distance;
    }

    @Override
    public void apply(ComplexField field) {
        new AngularSpectrumPropagator().propagate(field, distance);
    }

    @Override
    public void draw(GraphicsContext gc, double x, double y) {
        // nothing to draw
    }

    @Override
    public String getDescription() {
        return "Free space, d=" + String.format("%.4f", distance) + " m";
    }

    @Override
    public double getLength() {
        return distance;
    }
}