package com.optics.simulation.model;

import com.optics.simulation.ComplexField;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class GratingElement implements OpticalElement {
    private final double period;
    private final double amplitude;
    private final boolean rectangular;
    private double[][] cachedMask;
    private boolean maskCached = false;

    public GratingElement(double period, double amplitude, boolean rectangular) {
        this.period = period;
        this.amplitude = amplitude;
        this.rectangular = rectangular;
    }

    @Override
    public void apply(ComplexField field) {
        if (!maskCached || cachedMask.length != field.getN()) {
            int n = field.getN();
            double dx = field.getDx();
            double half = n / 2.0;
            cachedMask = new double[n][2 * n];
            for (int i = 0; i < n; i++) {
                double x = (i - half) * dx;
                for (int j = 0; j < n; j++) {
                    double y = (j - half) * dx;
                    double phase;
                    if (rectangular) {
                        phase = 0.0;
                        if (Math.sin(2.0 * Math.PI * x / period) >= 0) phase += amplitude;
                        if (Math.sin(2.0 * Math.PI * y / period) >= 0) phase += amplitude;
                    } else {
                        phase = amplitude * (Math.sin(2.0 * Math.PI * x / period)
                                + Math.sin(2.0 * Math.PI * y / period));
                    }
                    int idx = 2 * j;
                    cachedMask[i][idx] = Math.cos(phase);
                    cachedMask[i][idx + 1] = Math.sin(phase);
                }
            }
            maskCached = true;
        }
        field.applyPrecomputedMask(cachedMask);
    }

    @Override
    public void draw(GraphicsContext gc, double x, double y) {
        int halfHeight = 35;
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(2);
        int nLines = 12;
        double spacing = 50.0 / nLines;
        for (int i = 0; i < nLines; i++) {
            double xPos = x - 25 + i * spacing;
            gc.strokeLine(xPos, y - halfHeight, xPos, y + halfHeight);
        }
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(10));
        String label = "Grating " + (rectangular ? "rect" : "sin") + " p=" + String.format("%.4f", period);
        gc.fillText(label, x - 35, y - halfHeight - 8);
    }

    @Override
    public String getDescription() {
        return "Grating, p=" + String.format("%.4f", period) + " m, amp=" + amplitude +
                (rectangular ? ", rect" : ", sin");
    }

    public double getPeriod() {
        return period;
    }

    public double getAmplitude() {
        return amplitude;
    }

    public boolean isRectangular() {
        return rectangular;
    }
}