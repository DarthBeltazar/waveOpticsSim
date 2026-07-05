package com.optics.simulation;

import org.jtransforms.fft.DoubleFFT_2D;

public final class FFT2D {
    private FFT2D() {
    }

    public static void fft2(double[][] data) {
        int n = data.length;
        validate(data);
        new DoubleFFT_2D(n, n).complexForward(data);
    }

    public static void ifft2(double[][] data) {
        int n = data.length;
        validate(data);
        new DoubleFFT_2D(n, n).complexInverse(data, true);
    }

    private static void validate(double[][] data) {
        int n = data.length;
        if (n == 0 || data[0].length != 2 * n) {
            throw new IllegalArgumentException("Data must be square and interleaved (size N x 2N)");
        }
        if ((n & (n - 1)) != 0) {
            throw new IllegalArgumentException("Size must be a power of two");
        }
    }
}