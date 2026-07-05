package com.optics.simulation.model;

import com.optics.simulation.ComplexField;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class SlitElement implements OpticalElement {
    private final double width;
    private double[][] cachedMask;
    private boolean maskCached = false;

    public SlitElement(double width) {
        this.width = width;
    }

    @Override
    public void apply(ComplexField field) {
        if (!maskCached || cachedMask.length != field.getN()) {
            int n = field.getN();
            double dx = field.getDx();
            double half = n / 2.0;
            cachedMask = new double[n][2 * n];
            for (int j = 0; j < n; j++) {
                double x = (j - half) * dx;
                for (int i = 0; i < n; i++) {
                    int idx = 2 * j;
                    if (Math.abs(x) <= width / 2.0) {
                        cachedMask[i][idx] = 1.0;
                        cachedMask[i][idx + 1] = 0.0;
                    } else {
                        cachedMask[i][idx] = 0.0;
                        cachedMask[i][idx + 1] = 0.0;
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
        double halfWidthPx = Math.max(width * 30, 2);
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(2);
        gc.strokeLine(x - halfWidthPx, y - halfHeight, x - halfWidthPx, y + halfHeight);
        gc.strokeLine(x + halfWidthPx, y - halfHeight, x + halfWidthPx, y + halfHeight);
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(11));
        gc.fillText("Slit w=" + String.format("%.4f", width) + " m", x - 30, y - halfHeight - 8);
    }

    @Override
    public String getDescription() {
        return "Slit, w=" + String.format("%.4f", width) + " m";
    }

    public double getWidth() {
        return width;
    }
}