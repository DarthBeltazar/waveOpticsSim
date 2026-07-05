package com.optics.simulation;

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
}