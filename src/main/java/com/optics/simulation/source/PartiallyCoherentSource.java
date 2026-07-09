package com.optics.simulation.source;

import com.optics.simulation.ComplexField;
import com.optics.simulation.FFT2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Generates multiple realizations of a partially coherent field
 * using the Schell model with Gaussian correlation.
 */
public class PartiallyCoherentSource {

    private final int N;
    private final double dx;
    private final double lambda;
    private final double beamWidth;
    private final double coherenceLength;
    private final int numRealizations;
    private final Random random = new Random(0);

    public PartiallyCoherentSource(int N, double dx, double lambda,
                                   double beamWidth, double coherenceLength,
                                   int numRealizations) {
        this.N = N;
        this.dx = dx;
        this.lambda = lambda;
        this.beamWidth = beamWidth;
        this.coherenceLength = coherenceLength;
        this.numRealizations = numRealizations;
    }

    /**
     * Generates a list of complex fields (realizations).
     * Each field has the same amplitude profile (Gaussian) but random phase,
     * and the correlation between fields is enforced by filtering in frequency domain.
     */
    public List<ComplexField> generateFields() {
        List<ComplexField> fields = new ArrayList<>(numRealizations);
        double half = N / 2.0;

        // Precompute Gaussian amplitude profile
        double[][] amplitude = new double[N][N];
        for (int i = 0; i < N; i++) {
            double x = (i - half) * dx;
            for (int j = 0; j < N; j++) {
                double y = (j - half) * dx;
                amplitude[i][j] = Math.exp(-(x * x + y * y) / (beamWidth * beamWidth));
            }
        }

        // Precompute correlation filter in frequency domain
        double[][] filter = computeCorrelationFilter();

        // Generate realizations in parallel
        IntStream.range(0, numRealizations).parallel().forEach(iter -> {
            ComplexField field = generateSingleRealization(amplitude, filter);
            synchronized (fields) {
                fields.add(field);
            }
        });

        return fields;
    }

    private ComplexField generateSingleRealization(double[][] amplitude, double[][] filter) {
        int n = N;
        ComplexField field = new ComplexField(n, dx, lambda);
        double[][] data = field.getData();

        // 1. Start with Gaussian amplitude and random phase (uniform -π..π)
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double phase = 2.0 * Math.PI * random.nextDouble();
                double amp = amplitude[i][j];
                int idx = 2 * j;
                data[i][idx] = amp * Math.cos(phase);
                data[i][idx + 1] = amp * Math.sin(phase);
            }
        }

        // 2. Transform to frequency domain
        FFT2D.fft2(data);

        // 3. Multiply by the correlation filter (sqrt of spectral density)
        for (int i = 0; i < n; i++) {
            double[] rowData = data[i];
            double[] rowFilter = filter[i];
            for (int j = 0; j < n; j++) {
                int idx = 2 * j;
                double r = rowData[idx];
                double im = rowData[idx + 1];
                double fr = rowFilter[idx];
                double fi = rowFilter[idx + 1];
                // Complex multiplication: (r + i*im) * (fr + i*fi)
                rowData[idx] = r * fr - im * fi;
                rowData[idx + 1] = r * fi + im * fr;
            }
        }

        // 4. Inverse FFT to get the final field
        FFT2D.ifft2(data);

        return field;
    }

    /**
     * Computes the correlation filter in frequency domain.
     * For Gaussian correlation: μ(Δr) = exp(-Δr²/(2*Lc²)).
     * Its Fourier transform is also Gaussian: H(f) ~ exp(-2π² Lc² f²).
     */
    private double[][] computeCorrelationFilter() {
        int n = N;
        double[][] filter = new double[n][2 * n];
        double half = n / 2.0;

        // Spatial frequencies
        double[] fx = new double[n];
        double[] fy = new double[n];
        for (int i = 0; i < n; i++) {
            fx[i] = (i < n / 2) ? (double) i / (n * dx) : (double) (i - n) / (n * dx);
            fy[i] = (i < n / 2) ? (double) i / (n * dx) : (double) (i - n) / (n * dx);
        }

        // Filter = sqrt( F[μ] ) = exp(-2π² Lc² f²)
        for (int i = 0; i < n; i++) {
            double fxi = fx[i];
            for (int j = 0; j < n; j++) {
                double fyj = fy[j];
                double f2 = fxi * fxi + fyj * fyj;
                double val = Math.exp(-2.0 * Math.PI * Math.PI * coherenceLength * coherenceLength * f2);
                int idx = 2 * j;
                filter[i][idx] = val;
                filter[i][idx + 1] = 0.0; // filter is real
            }
        }
        return filter;
    }
}