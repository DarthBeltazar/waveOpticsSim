package com.optics.simulation.model;

import com.optics.simulation.ComplexField;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;

public class LensElement implements OpticalElement {
    private final double focalLength;
    private double[][] cachedMask;
    private boolean maskCached = false;

    public LensElement(double focalLength) {
        this.focalLength = focalLength;
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
            maskCached = true;
        }
        field.applyPrecomputedMask(cachedMask);
    }

    @Override
    public void draw(GraphicsContext gc, double x, double y) {
        int halfHeight = 35;
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(2.5);
        gc.strokeArc(x - 18, y - halfHeight, 36, halfHeight * 2, -90, 180, ArcType.OPEN);
        gc.strokeArc(x - 18, y - halfHeight, 36, halfHeight * 2, 90, 180, ArcType.OPEN);
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(11));
        gc.fillText("Lens f=" + String.format("%.3f", focalLength) + " m", x - 30, y - halfHeight - 8);
        if (focalLength > 0) {
            gc.setFill(Color.RED);
            double fx = x + focalLength * 30;
            gc.fillOval(fx - 3, y - 3, 6, 6);
            gc.fillText("F", fx + 4, y - 5);
        }
    }

    @Override
    public String getDescription() {
        return "Lens, f=" + String.format("%.4f", focalLength) + " m";
    }

    public double getFocalLength() {
        return focalLength;
    }
}