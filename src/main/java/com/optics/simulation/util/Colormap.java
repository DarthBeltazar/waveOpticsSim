package com.optics.simulation.util;

import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Predefined color maps for 2D visualization.
 */
public enum Colormap {
    GRAYSCALE("Grayscale"),
    VIRIDIS("Viridis"),
    JET("Jet"),
    HOT("Hot"),
    COOL("Cool"),
    PLASMA("Plasma");

    private final String displayName;
    private static final Map<String, Colormap> BY_NAME = new HashMap<>();

    static {
        for (Colormap cm : values()) {
            BY_NAME.put(cm.displayName, cm);
        }
    }

    Colormap(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Colormap fromDisplayName(String name) {
        return BY_NAME.get(name);
    }

    /**
     * Maps a value in [0,1] to a Color.
     */
    public Color apply(double t) {
        // Clamp
        t = Math.min(1.0, Math.max(0.0, t));
        switch (this) {
            case GRAYSCALE:
                return Color.gray(t);
            case VIRIDIS:
                return viridis(t);
            case JET:
                return jet(t);
            case HOT:
                return hot(t);
            case COOL:
                return cool(t);
            case PLASMA:
                return plasma(t);
            default:
                return Color.gray(t);
        }
    }

    // ---------- Colormap implementations ----------

    private static Color viridis(double t) {
        // Simplified viridis (using predefined RGB points)
        double r, g, b;
        if (t < 0.0) { r=0.267; g=0.004; b=0.329; }
        else if (t < 0.25) { double s = t / 0.25; r = 0.267 + s*(0.253); g = 0.004 + s*(0.265); b = 0.329 + s*(0.015); }
        else if (t < 0.5) { double s = (t-0.25)/0.25; r = 0.520 + s*(-0.086); g = 0.269 + s*(0.249); b = 0.344 + s*(0.031); }
        else if (t < 0.75) { double s = (t-0.5)/0.25; r = 0.434 + s*(0.046); g = 0.518 + s*(0.237); b = 0.375 + s*(0.075); }
        else { double s = (t-0.75)/0.25; r = 0.480 + s*(0.177); g = 0.755 + s*(0.151); b = 0.450 + s*(-0.075); }
        return Color.color(r, g, b);
    }

    private static Color jet(double t) {
        double r, g, b;
        if (t < 0.125) {
            r = 0.0; g = 0.0; b = 0.5 + 4.0 * t;
        } else if (t < 0.375) {
            r = 0.0; g = 4.0 * t - 0.5; b = 1.0;
        } else if (t < 0.625) {
            r = 4.0 * t - 1.5; g = 1.0; b = -4.0 * t + 2.5;
        } else if (t < 0.875) {
            r = 1.0; g = -4.0 * t + 3.5; b = 0.0;
        } else {
            r = -4.0 * t + 4.5; g = 0.0; b = 0.0;
        }
        return Color.color(clamp(r), clamp(g), clamp(b));
    }

    private static Color hot(double t) {
        double r = Math.min(1.0, 4.0 * t);
        double g = Math.min(1.0, 4.0 * t - 1.0);
        double b = Math.min(1.0, 4.0 * t - 2.0);
        return Color.color(clamp(r), clamp(g), clamp(b));
    }

    private static Color cool(double t) {
        return Color.color(t, 1.0 - t, 1.0);
    }

    private static Color plasma(double t) {
        // Approximate plasma
        double r = 0.0, g = 0.0, b = 0.0;
        if (t < 0.25) {
            r = 0.0; g = 0.0 + 4*t; b = 0.5 + 2*t;
        } else if (t < 0.5) {
            r = 4*t - 1.0; g = 0.0 + 4*(t-0.25); b = 1.0;
        } else if (t < 0.75) {
            r = 1.0; g = 4*(t-0.5); b = 1.0 - 4*(t-0.5);
        } else {
            r = 1.0 - 4*(t-0.75); g = 1.0; b = 0.5 - 4*(t-0.75);
        }
        return Color.color(clamp(r), clamp(g), clamp(b));
    }

    private static double clamp(double v) {
        return Math.min(1.0, Math.max(0.0, v));
    }
}