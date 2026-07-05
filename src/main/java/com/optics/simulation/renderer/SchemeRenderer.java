package com.optics.simulation.renderer;

import com.optics.simulation.model.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

public class SchemeRenderer {
    private final Canvas canvas;

    public SchemeRenderer(Canvas canvas) {
        this.canvas = canvas;
    }

    // Legacy method
    public void draw(List<? extends OpticalElement> elements) {
        draw(elements, false);
    }

    public void draw(List<? extends OpticalElement> elements, boolean pointSource) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double canvasW = canvas.getWidth();
        double canvasH = canvas.getHeight();

        double leftMargin = 60;
        double rightMargin = 60;
        double axisY = canvasH / 2 + 10;

        // Calculate total length for scaling
        double totalLength = 0.02; // source offset
        for (OpticalElement e : elements) {
            totalLength += e.getLength();
        }
        if (totalLength < 0.01) totalLength = 0.01;
        double scaleX = (canvasW - leftMargin - rightMargin) / totalLength;

        java.util.function.DoubleUnaryOperator toPixelX = (x) -> leftMargin + x * scaleX;

        // Draw axis
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);
        gc.strokeLine(leftMargin, axisY, canvasW - rightMargin, axisY);
        gc.strokeLine(canvasW - rightMargin, axisY, canvasW - rightMargin - 10, axisY - 5);
        gc.strokeLine(canvasW - rightMargin, axisY, canvasW - rightMargin - 10, axisY + 5);
        gc.fillText("z", canvasW - rightMargin - 15, axisY - 8);

        // Source
        double sourceX = toPixelX.applyAsDouble(0);
        gc.setFill(Color.BLUE);
        gc.fillOval(sourceX - 6, axisY - 6, 12, 12);
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(11));
        gc.fillText(pointSource ? "Point source" : "Collimated source", sourceX - 30, axisY - 14);

        // Build placements
        List<Placement> placements = new ArrayList<>();
        double currentX = 0.02;
        for (OpticalElement e : elements) {
            placements.add(new Placement(currentX, e));
            currentX += e.getLength();
        }
        double finalX = currentX;
        placements.add(new Placement(finalX, null));

        // Distance labels
        gc.setFont(Font.font(10));
        gc.setStroke(Color.RED);
        gc.setLineWidth(1);
        for (int i = 0; i < placements.size() - 1; i++) {
            Placement p1 = placements.get(i);
            Placement p2 = placements.get(i + 1);
            if (p1.elem instanceof FreeSpaceElement) {
                double x1 = toPixelX.applyAsDouble(p1.x);
                double x2 = toPixelX.applyAsDouble(p2.x);
                double y1 = axisY + 25;
                double y2 = axisY + 25;
                gc.setStroke(Color.RED);
                gc.setLineDashes(3, 3);
                gc.strokeLine(x1, y1, x2, y2);
                gc.setLineDashes(null);
                double midX = (x1 + x2) / 2;
                double dist = p1.elem.getLength();
                gc.fillText("d=" + String.format("%.3f", dist) + " m", midX - 25, y1 + 14);
            }
        }

        // Draw elements
        for (Placement p : placements) {
            if (p.elem == null) continue;
            double x = toPixelX.applyAsDouble(p.x);

            if (p.elem instanceof LensElement) {
                drawLens(gc, x, axisY, ((LensElement) p.elem).getFocalLength());
            } else if (p.elem instanceof MirrorElement m) {
                drawMirror(gc, x, axisY, m.isFlat(), m.getFocalLength());
            } else if (p.elem instanceof GratingElement g) {
                drawGrating(gc, x, axisY, g.getPeriod(), g.getAmplitude(), g.isRectangular());
            } else if (p.elem instanceof SlitElement) {
                drawSlit(gc, x, axisY, ((SlitElement) p.elem).getWidth());
            }
            // FreeSpaceElement does not draw anything
        }

        // Observation plane
        double obsX = toPixelX.applyAsDouble(finalX);
        gc.setStroke(Color.GREEN);
        gc.setLineWidth(1.5);
        gc.strokeLine(obsX, axisY - 30, obsX, axisY + 30);
        gc.fillText("Observation", obsX - 30, axisY - 36);

        // Draw rays
        drawRays(gc, placements, toPixelX, axisY, pointSource);

    }

    // Drawing primitives
    private void drawLens(GraphicsContext gc, double x, double y, double f) {
        int halfHeight = 35;
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(2.5);
        gc.strokeArc(x - 18, y - halfHeight, 36, halfHeight * 2, -90, 180, javafx.scene.shape.ArcType.OPEN);
        gc.strokeArc(x - 18, y - halfHeight, 36, halfHeight * 2, 90, 180, javafx.scene.shape.ArcType.OPEN);
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(11));
        gc.fillText("Lens f=" + String.format("%.3f", f) + " m", x - 30, y - halfHeight - 8);
        if (f > 0) {
            gc.setFill(Color.RED);
            double fx = x + f * 30;
            gc.fillOval(fx - 3, y - 3, 6, 6);
            gc.fillText("F", fx + 4, y - 5);
        }
    }

    private void drawMirror(GraphicsContext gc, double x, double y, boolean flat, double f) {
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
            gc.strokeArc(x - 20, y - halfHeight, 40, halfHeight * 2, 90, 180, javafx.scene.shape.ArcType.OPEN);
            gc.fillText("Mirror f=" + String.format("%.3f", f), x - 30, y - halfHeight - 8);
        }
    }

    private void drawGrating(GraphicsContext gc, double x, double y, double period, double amp, boolean rect) {
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
        String label = "Grating " + (rect ? "rect" : "sin") + " p=" + String.format("%.4f", period);
        gc.fillText(label, x - 35, y - halfHeight - 8);
    }

    private void drawSlit(GraphicsContext gc, double x, double y, double width) {
        int halfHeight = 35;
        double halfWidthPx = Math.max(width * 30, 2); // ensure visible even for tiny width
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(2);
        gc.strokeLine(x - halfWidthPx, y - halfHeight, x - halfWidthPx, y + halfHeight);
        gc.strokeLine(x + halfWidthPx, y - halfHeight, x + halfWidthPx, y + halfHeight);
        gc.setFill(Color.BLACK);
        gc.setFont(Font.font(11));
        gc.fillText("Slit w=" + String.format("%.4f", width) + " m", x - 30, y - halfHeight - 8);
    }

    private void drawRays(GraphicsContext gc, List<Placement> placements,
                          java.util.function.DoubleUnaryOperator toPixelX, double axisY,
                          boolean pointSource) {
        if (placements.size() < 2) return;

        int numRays = 5;
        double[] startHeights;
        double[] startAngles;

        if (pointSource) {
            startAngles = new double[]{-0.6, -0.3, 0.0, 0.3, 0.6};
            startHeights = new double[numRays];
        } else {
            startHeights = new double[]{-0.0015, -0.00075, 0.0, 0.00075, 0.0015};
            startAngles = new double[numRays];
        }

        for (int idx = 0; idx < numRays; idx++) {
            List<double[]> points = new ArrayList<>();
            double currentX = 0.02;
            double currentY = pointSource ? 0.0 : startHeights[idx];
            double currentSlope = pointSource ? Math.tan(startAngles[idx]) : 0.0;

            points.add(new double[]{0.0, currentY});

            for (int i = 0; i < placements.size(); i++) {
                Placement p = placements.get(i);
                if (p.elem == null) continue;
                double elemX = p.x;
                double dx = elemX - currentX;
                currentY += currentSlope * dx;
                currentX = elemX;
                points.add(new double[]{currentX, currentY});

                // Apply element transformation
                if (p.elem instanceof LensElement) {
                    double f = ((LensElement) p.elem).getFocalLength();
                    if (f != 0) currentSlope = currentSlope - currentY / f;
                } else if (p.elem instanceof MirrorElement m) {
                    if (m.isFlat()) {
                        currentSlope = -currentSlope;
                    } else {
                        double f = m.getFocalLength();
                        currentSlope = -currentSlope - currentY / f;
                    }
                } else if (p.elem instanceof GratingElement) {
                    double amp = ((GratingElement) p.elem).getAmplitude();
                    currentSlope += amp * 0.01 * (currentY > 0 ? 1 : -1);
                }
                // SlitElement does not change ray direction
                // FreeSpaceElement: no change
            }

            Placement last = placements.get(placements.size() - 1);
            double obsX = last.x;
            double dxObs = obsX - currentX;
            currentY += currentSlope * dxObs;
            currentX = obsX;
            points.add(new double[]{currentX, currentY});

            gc.setStroke(Color.ORANGE);
            gc.setLineWidth(1.5);
            for (int i = 0; i < points.size() - 1; i++) {
                double x1 = toPixelX.applyAsDouble(points.get(i)[0]);
                double y1 = axisY - points.get(i)[1] * 800;
                double x2 = toPixelX.applyAsDouble(points.get(i + 1)[0]);
                double y2 = axisY - points.get(i + 1)[1] * 800;
                gc.strokeLine(x1, y1, x2, y2);
            }
        }
    }

    private static class Placement {
        double x;
        OpticalElement elem;

        Placement(double x, OpticalElement elem) {
            this.x = x;
            this.elem = elem;
        }
    }
}