package com.optics.simulation.model;

import com.optics.simulation.ComplexField;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;

public class MirrorElement implements OpticalElement {
    private final boolean flat;
    private final double focalLength;
    private double[][] cachedMask;
    private boolean maskCached = false;

    public MirrorElement(boolean flat, double focalLength) {
        this.flat = flat;
        this.focalLength = focalLength;
    }

    public MirrorElement(boolean flat) {
        this(flat, 0.0);
    }

    @Override
    public void apply(ComplexField field) {
        if (!maskCached || cachedMask.length != field.getN()) {
            int n = field.getN();
            double dx = field.getDx();
            double lambda = field.getLambda();
            double k = 2.0 * Math.PI / lambda;
            double half = n / 2.0;
            cachedMask = new double[n][2 * n];
            if (flat) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        int idx = 2 * j;
                        cachedMask[i][idx] = -1.0;
                        cachedMask[i][idx + 1] = 0.0;
                    }
                }
            } else {
                for (int i = 0; i < n; i++) {
                    double x = (i - half) * dx;
                    for (int j = 0; j < n; j++) {
                        double y = (j - half) * dx;
                        double phase = -k * (x * x + y * y) / (2.0 * focalLength);
                        int idx = 2 * j;
                        cachedMask[i][idx] = Math.cos(phase);
                        cachedMask[i][idx + 1] = Math.sin(phase);
                    }
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
        gc.setLineWidth(2.5);
        if (flat) {
            gc.strokeLine(x, y - halfHeight, x, y + halfHeight);
            for (int i = -halfHeight + 5; i < halfHeight - 5; i += 8) {
                gc.strokeLine(x - 4, y + i, x, y + i + 4);
            }
            gc.fillText("Mirror (flat)", x - 30, y - halfHeight - 8);
        } else {
            gc.strokeArc(x - 20, y - halfHeight, 40, halfHeight * 2, 90, 180, ArcType.OPEN);
            gc.fillText("Mirror f=" + String.format("%.3f", focalLength), x - 30, y - halfHeight - 8);
        }
    }

    @Override
    public String getDescription() {
        if (flat) return "Mirror, flat";
        else return "Mirror, f=" + String.format("%.4f", focalLength) + " m";
    }

    public boolean isFlat() {
        return flat;
    }

    public double getFocalLength() {
        return focalLength;
    }
}