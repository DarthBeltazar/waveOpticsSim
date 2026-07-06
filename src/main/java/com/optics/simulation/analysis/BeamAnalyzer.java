package com.optics.simulation.analysis;

import com.optics.simulation.ComplexField;

public class BeamAnalyzer {

    private final ComplexField field;
    private final double[][] intensity;
    private final double dx;
    private final int n;
    private final double half;

    private double totalIntensity;
    private double centerX, centerY;
    private double sigmaX, sigmaY;
    private double peakIntensity;
    private double fwhmX, fwhmY;

    public BeamAnalyzer(ComplexField field) {
        this.field = field;
        this.intensity = field.computeIntensity();
        this.dx = field.getDx();
        this.n = field.getN();
        this.half = n / 2.0;
        compute();
    }

    private void compute() {
        totalIntensity = 0.0;
        double sumX = 0.0, sumY = 0.0;
        peakIntensity = 0.0;

        for (int i = 0; i < n; i++) {
            double x = (i - half) * dx;
            for (int j = 0; j < n; j++) {
                double y = (j - half) * dx;
                double I = intensity[i][j];
                if (I < 0) I = 0;
                totalIntensity += I;
                sumX += x * I;
                sumY += y * I;
                if (I > peakIntensity) peakIntensity = I;
            }
        }

        if (totalIntensity > 0) {
            centerX = sumX / totalIntensity;
            centerY = sumY / totalIntensity;
        } else {
            centerX = 0;
            centerY = 0;
        }

        double m2x = 0.0, m2y = 0.0;
        for (int i = 0; i < n; i++) {
            double x = (i - half) * dx;
            for (int j = 0; j < n; j++) {
                double y = (j - half) * dx;
                double I = intensity[i][j];
                if (I < 0) I = 0;
                m2x += (x - centerX) * (x - centerX) * I;
                m2y += (y - centerY) * (y - centerY) * I;
            }
        }
        if (totalIntensity > 0) {
            sigmaX = Math.sqrt(m2x / totalIntensity);
            sigmaY = Math.sqrt(m2y / totalIntensity);
        } else {
            sigmaX = 0;
            sigmaY = 0;
        }

        double wX = 2 * sigmaX;
        double wY = 2 * sigmaY;

        fwhmX = 2.355 * sigmaX;
        fwhmY = 2.355 * sigmaY;
    }

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getSigmaX() { return sigmaX; }
    public double getSigmaY() { return sigmaY; }
    public double getRadiusX() { return 2 * sigmaX; }
    public double getRadiusY() { return 2 * sigmaY; }
    public double getFwhmX() { return fwhmX; }
    public double getFwhmY() { return fwhmY; }
    public double getPeakIntensity() { return peakIntensity; }
    public double getTotalIntensity() { return totalIntensity; }

    @Override
    public String toString() {
        return String.format(
                "Beam Analysis:\n" +
                        "Center: (%.4e, %.4e) m\n" +
                        "Sigma: (%.4e, %.4e) m\n" +
                        "Radius (1/e²): (%.4e, %.4e) m\n" +
                        "FWHM: (%.4e, %.4e) m\n" +
                        "Peak intensity: %.4e\n" +
                        "Total power: %.4e",
                centerX, centerY,
                sigmaX, sigmaY,
                getRadiusX(), getRadiusY(),
                fwhmX, fwhmY,
                peakIntensity,
                totalIntensity
        );
    }
}