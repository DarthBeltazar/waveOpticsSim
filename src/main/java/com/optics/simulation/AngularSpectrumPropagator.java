package com.optics.simulation;

import java.util.concurrent.ConcurrentHashMap;

public class AngularSpectrumPropagator implements Propagator {
    private static final ConcurrentHashMap<String, double[][]> transferCache = new ConcurrentHashMap<>();
    private static boolean antiAliasingEnabled = true;

    public static void setAntiAliasingEnabled(boolean enabled) {
        antiAliasingEnabled = enabled;
    }

    @Override
    public void propagate(ComplexField field, double distance) {
        if (antiAliasingEnabled) {
            propagateWithZeroPadding(field, distance);
        } else {
            propagateStandard(field, distance);
        }
    }

    public void propagateStandard(ComplexField field, double distance) {
        int n = field.getN();
        double dx = field.getDx();
        double lambda = field.getLambda();
        double k = 2.0 * Math.PI / lambda;

        String key = n + "_" + dx + "_" + lambda + "_" + distance;
        double[][] H = transferCache.get(key);
        if (H == null) {
            H = computeTransferFunction(n, dx, lambda, k, distance);
            transferCache.put(key, H);
        }

        double[][] data = field.getData();

        // Forward FFT
        FFT2D.fft2(data);

        // Multiply by H (interleaved)
        for (int i = 0; i < n; i++) {
            double[] rowData = data[i];
            double[] rowH = H[i];
            for (int j = 0; j < n; j++) {
                int idx = 2 * j;
                double r = rowData[idx];
                double im = rowData[idx + 1];
                double hr = rowH[idx];
                double hi = rowH[idx + 1];
                rowData[idx] = r * hr - im * hi;
                rowData[idx + 1] = r * hi + im * hr;
            }
        }

        // Inverse FFT
        FFT2D.ifft2(data);
    }

    private void propagateWithZeroPadding(ComplexField field, double distance) {
        int n = field.getN();
        int paddedN = n * 2;
        double dx = field.getDx();
        double lambda = field.getLambda();
        double k = 2.0 * Math.PI / lambda;

        String key = paddedN + "_" + dx + "_" + lambda + "_" + distance;
        double[][] H = transferCache.get(key);
        if (H == null) {
            H = computeTransferFunction(paddedN, dx, lambda, k, distance);
            transferCache.put(key, H);
        }

        double[][] paddedData = new double[paddedN][2 * paddedN];
        double[][] originalData = field.getData();

        int offset = n / 2;
        for (int i = 0; i < n; i++) {
            System.arraycopy(originalData[i], 0, paddedData[i + offset], 2 * offset, 2 * n);
        }

        // Straight FFT on scaled massive
        FFT2D.fft2(paddedData);

        // Умножение на передаточную функцию
        for (int i = 0; i < paddedN; i++) {
            double[] rowData = paddedData[i];
            double[] rowH = H[i];
            for (int j = 0; j < paddedN; j++) {
                int idx = 2 * j;
                double r = rowData[idx];
                double im = rowData[idx + 1];
                double hr = rowH[idx];
                double hi = rowH[idx + 1];
                rowData[idx] = r * hr - im * hi;
                rowData[idx + 1] = r * hi + im * hr;
            }
        }

        // Обратное БПФ
        FFT2D.ifft2(paddedData);

        // Копируем центральную часть обратно в исходное поле
        for (int i = 0; i < n; i++) {
            System.arraycopy(paddedData[i + offset], 2 * offset, originalData[i], 0, 2 * n);
        }
    }

    private double[][] computeTransferFunction(int n, double dx, double lambda, double k, double distance) {
        double[] fx = new double[n];
        double[] fy = new double[n];
        for (int i = 0; i < n; i++) {
            fx[i] = (i < n / 2) ? (double) i / (n * dx) : (double) (i - n) / (n * dx);
            fy[i] = (i < n / 2) ? (double) i / (n * dx) : (double) (i - n) / (n * dx);
        }
        double[][] H = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            double fxi = fx[i];
            for (int j = 0; j < n; j++) {
                double fyj = fy[j];
                double arg = 1.0 - (lambda * fxi) * (lambda * fxi) - (lambda * fyj) * (lambda * fyj);
                int idx = 2 * j;
                if (arg < 0) {
                    H[i][idx] = 0.0;
                    H[i][idx + 1] = 0.0;
                } else {
                    double phase = k * distance * Math.sqrt(arg);
                    H[i][idx] = Math.cos(phase);
                    H[i][idx + 1] = Math.sin(phase);
                }
            }
        }
        return H;
    }
}