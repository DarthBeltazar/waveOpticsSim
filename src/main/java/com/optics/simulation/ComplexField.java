package com.optics.simulation;

import java.io.IOException;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.IntStream;

/**
 * Complex field stored in interleaved format: data[i][2*j] = real, data[i][2*j+1] = imag.
 * This allows direct use with JTransforms without extra copying.
 */
public class ComplexField {
    private final int n;
    private final double dx;
    private final double lambda;
    private final double[][] data; // [n][2*n]

    public ComplexField(int n, double dx, double lambda) {
        if ((n & (n - 1)) != 0) throw new IllegalArgumentException("N must be power of two");
        this.n = n;
        this.dx = dx;
        this.lambda = lambda;
        this.data = new double[n][2 * n];
    }

    public int getN() {
        return n;
    }

    public double getDx() {
        return dx;
    }

    public double getLambda() {
        return lambda;
    }

    public double[][] getData() {
        return data;
    }

    public double getReal(int i, int j) {
        return data[i][2 * j];
    }

    public double getImag(int i, int j) {
        return data[i][2 * j + 1];
    }

    public void setValue(int i, int j, double r, double im) {
        data[i][2 * j] = r;
        data[i][2 * j + 1] = im;
    }

    /**
     * Applies a phase mask using a function φ(x,y) → phase.
     * Field = Field * exp(i*φ).
     */
    public void applyPhaseMask(DoubleBinaryOperator phaseFunc) {
        double half = n / 2.0;
        for (int i = 0; i < n; i++) {
            double x = (i - half) * dx;
            double[] row = data[i];
            for (int j = 0; j < n; j++) {
                double y = (j - half) * dx;
                double phi = phaseFunc.applyAsDouble(x, y);
                double cos = Math.cos(phi);
                double sin = Math.sin(phi);
                int idx = 2 * j;
                double r = row[idx];
                double im = row[idx + 1];
                row[idx] = r * cos - im * sin;
                row[idx + 1] = r * sin + im * cos;
            }
        }
    }

    /**
     * Applies a precomputed mask stored in interleaved format.
     * maskData[i][2*j] = Re(mask), maskData[i][2*j+1] = Im(mask).
     */
    public void applyPrecomputedMask(double[][] maskData) {
        IntStream.range(0, n).parallel().forEach(i -> {
            double[] row = data[i];
            double[] maskRow = maskData[i];
            for (int j = 0; j < n; j++) {
                int idx = 2 * j;
                double r = row[idx];
                double im = row[idx + 1];
                double mr = maskRow[idx];
                double mi = maskRow[idx + 1];
                row[idx] = r * mr - im * mi;
                row[idx + 1] = r * mi + im * mr;
            }
        });
    }

    /**
     * Computes intensity |E|² as a 2D array.
     */
    public double[][] computeIntensity() {
        double[][] intensity = new double[n][n];
        for (int i = 0; i < n; i++) {
            double[] row = data[i];
            double[] outRow = intensity[i];
            for (int j = 0; j < n; j++) {
                int idx = 2 * j;
                double r = row[idx];
                double im = row[idx + 1];
                outRow[j] = r * r + im * im;
            }
        }
        return intensity;
    }

    /**
     * Computes phase arg(E) as a 2D array (radians, range -π..π).
     */
    public double[][] computePhase() {
        double[][] phase = new double[n][n];
        for (int i = 0; i < n; i++) {
            double[] row = data[i];
            double[] outRow = phase[i];
            for (int j = 0; j < n; j++) {
                int idx = 2 * j;
                outRow[j] = Math.atan2(row[idx + 1], row[idx]);
            }
        }
        return phase;
    }

    // Convenience save methods (delegating to ImageUtils)
    public void saveIntensity(String filename, boolean logScale) throws IOException {
        double[][] intensity = computeIntensity();
        if (logScale) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    intensity[i][j] = Math.log1p(intensity[i][j]);
                }
            }
        }
        ImageUtils.saveImage(intensity, filename);
    }

    public void savePhase(String filename) throws IOException {
        ImageUtils.saveImage(computePhase(), filename);
    }
}