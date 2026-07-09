package com.optics.simulation.util;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility for converting 2D double arrays to PNG files and JavaFX Images.
 */
public final class ImageUtils {

    private ImageUtils() {
    }

    /**
     * Creates a colored 2D image from data.
     */
    public static Image createColoredImage(double[][] data, Colormap colormap, boolean logScale) {
        int n = data.length;
        int m = data[0].length;
        WritableImage image = new WritableImage(m, n);
        PixelWriter writer = image.getPixelWriter();

        // Preprocess: log scale and NaN/Inf handling
        double[][] processed = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double val = data[i][j];
                if (!Double.isFinite(val)) val = 0.0;
                if (logScale) {
                    processed[i][j] = Math.log1p(val);
                } else {
                    processed[i][j] = val;
                }
            }
        }

        // Find min/max
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double v = processed[i][j];
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        double range = max - min;
        if (range < 1e-12) {
            // All same value: fill with gray or colormap at 0.5
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    writer.setColor(j, i, colormap.apply(0.5));
                }
            }
            return image;
        }

        // Write pixels
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double val = (processed[i][j] - min) / range;
                Color color = colormap.apply(val);
                writer.setColor(j, i, color);
            }
        }
        return image;
    }

    /**
     * Saves a 2D double array as a grayscale PNG.
     * Values are linearly normalized: min -> 0, max -> 255.
     */
    public static void saveImage(double[][] data, String filename) throws IOException {
        int n = data.length;
        int m = data[0].length;
        BufferedImage image = new BufferedImage(m, n, BufferedImage.TYPE_BYTE_GRAY);

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double[] row : data) {
            for (double v : row) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        double range = max - min;
        if (range < 1e-12) range = 1.0;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                int gray = (int) (255.0 * (data[i][j] - min) / range);
                gray = Math.min(255, Math.max(0, gray));
                image.setRGB(j, i, (gray << 16) | (gray << 8) | gray);
            }
        }

        ImageIO.write(image, "png", new File(filename));
    }

    /**
     * Creates a JavaFX Image from a 2D double array.
     * The data is normalized to [0,1] before creating the image.
     *
     * @param logScale if true, applies log(1+x) transform
     */
    public static Image createImage(double[][] data, boolean logScale) {
        int n = data.length;
        int m = data[0].length;
        WritableImage image = new WritableImage(m, n);
        PixelWriter writer = image.getPixelWriter();

        double[][] processed = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double val = data[i][j];
                if (!Double.isFinite(val)) val = 0.0;
                if (logScale) {
                    processed[i][j] = Math.log1p(val);
                } else {
                    processed[i][j] = val;
                }
            }
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double[] row : processed) {
            for (double v : row) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }

        double range = max - min;
        if (range < 1e-12) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    writer.setColor(j, i, Color.GRAY);
                }
            }
            return image;
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double val = (processed[i][j] - min) / range;
                int gray = (int) Math.round(val * 255.0);
                gray = Math.min(255, Math.max(0, gray));
                writer.setColor(j, i, Color.rgb(gray, gray, gray));
            }
        }
        return image;
    }

    public static void saveColoredImage(double[][] data, Colormap colormap, boolean logScale, String filename) throws IOException {
        int n = data.length;
        int m = data[0].length;
        BufferedImage image = new BufferedImage(m, n, BufferedImage.TYPE_INT_RGB);

        double[][] processed = preprocess(data, logScale);
        double min = findMin(processed);
        double max = findMax(processed);
        double range = max - min;
        if (range < 1e-12) range = 1.0;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double val = (processed[i][j] - min) / range;
                Color color = colormap.apply(val);
                int rgb = colorToInt(color);
                image.setRGB(j, i, rgb);
            }
        }
        ImageIO.write(image, "png", new File(filename));
    }

    private static double[][] preprocess(double[][] data, boolean logScale) {
        int n = data.length, m = data[0].length;
        double[][] processed = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double val = data[i][j];
                if (!Double.isFinite(val)) val = 0.0;
                if (logScale) processed[i][j] = Math.log1p(val);
                else processed[i][j] = val;
            }
        }
        return processed;
    }

    public static double findMin(double[][] data) {
        double min = Double.POSITIVE_INFINITY;
        for (double[] row : data)
            for (double v : row)
                if (v < min) min = v;
        return min;
    }

    public static double findMax(double[][] data) {
        double max = Double.NEGATIVE_INFINITY;
        for (double[] row : data)
            for (double v : row)
                if (v > max) max = v;
        return max;
    }

    private static int colorToInt(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return (r << 16) | (g << 8) | b;
    }

    public static void drawColorbar(Canvas canvas, Colormap colormap, double min, double max, String label) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        int steps = 256;
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            Color color = colormap.apply(t);
            gc.setFill(color);
            gc.fillRect(0, h - (i + 1) * h / steps, w, h / steps);
        }
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font(10));
        gc.fillText(String.format("%.2e", max), 2, 12);
        gc.fillText(String.format("%.2e", min), 2, h - 4);
        if (label != null) {
            gc.save();
            gc.translate(8, h / 2);
            gc.rotate(-90);
            gc.fillText(label, 0, 0);
            gc.restore();
        }
    }

    /**
     * Creates a color JavaFX Image from three intensity components (R, G, B).
     *
     * @param red      red intensity (normalized or raw)
     * @param green    green intensity
     * @param blue     blue intensity
     * @param logScale if true, apply log1p to each component
     * @return JavaFX Image
     */
    public static javafx.scene.image.Image createColorImage(double[][] red, double[][] green, double[][] blue, boolean logScale) {
        int n = red.length;
        int m = red[0].length;
        WritableImage image = new WritableImage(m, n);
        PixelWriter writer = image.getPixelWriter();

        // Нормализуем каждую компоненту отдельно (можно и вместе, но отдельно даёт лучший контраст)
        double[][] r = preprocess(red, logScale);
        double[][] g = preprocess(green, logScale);
        double[][] b = preprocess(blue, logScale);

        double minR = findMin(r), maxR = findMax(r);
        double minG = findMin(g), maxG = findMax(g);
        double minB = findMin(b), maxB = findMax(b);
        double rangeR = maxR - minR;
        double rangeG = maxG - minG;
        double rangeB = maxB - minB;
        if (rangeR < 1e-12) rangeR = 1.0;
        if (rangeG < 1e-12) rangeG = 1.0;
        if (rangeB < 1e-12) rangeB = 1.0;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double vr = (r[i][j] - minR) / rangeR;
                double vg = (g[i][j] - minG) / rangeG;
                double vb = (b[i][j] - minB) / rangeB;
                Color color = Color.color(vr, vg, vb);
                writer.setColor(j, i, color);
            }
        }
        return image;
    }

    /**
     * Saves a color image to PNG.
     */
    public static void saveColorImage(double[][] data, Colormap colormap, boolean logScale, String filename) throws IOException {
        int n = data.length;
        int m = data[0].length;
        BufferedImage image = new BufferedImage(m, n, BufferedImage.TYPE_INT_RGB);

        double[][] processed = preprocess(data, logScale);
        double min = findMin(processed);
        double max = findMax(processed);
        double range = max - min;
        if (range < 1e-12) range = 1.0;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double val = (processed[i][j] - min) / range;
                java.awt.Color color = colormap.applyAWT(val);
                int rgb = color.getRGB();
                image.setRGB(j, i, rgb);
            }
        }
        ImageIO.write(image, "png", new File(filename));
    }
}