package com.optics.simulation.model;

import com.optics.simulation.ComplexField;
import javafx.scene.canvas.GraphicsContext;

/**
 * Represents an optical element in the simulation.
 * Each element can be applied to a field and drawn on a scheme.
 */
public interface OpticalElement {
    /**
     * Applies the element's effect to the complex field.
     */
    void apply(ComplexField field);

    /**
     * Draws a schematic representation of the element on a canvas.
     *
     * @param gc graphics context
     * @param x  x-coordinate (in pixel space)
     * @param y  y-coordinate of the optical axis (in pixel space)
     */
    void draw(GraphicsContext gc, double x, double y);

    /**
     * Returns a human-readable description for the UI list.
     */
    String getDescription();

    /**
     * Returns the physical position offset (for free space propagation).
     * Most elements have zero offset except FreeSpace.
     */
    default double getLength() {
        return 0.0;
    }
}