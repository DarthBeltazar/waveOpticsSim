package com.optics.simulation.factory;

import com.optics.simulation.model.*;

public class ElementFactory {

    public static OpticalElement createFreeSpace(double distance) {
        return new FreeSpaceElement(distance);
    }

    public static OpticalElement createLens(double focalLength) {
        return new LensElement(focalLength);
    }

    public static OpticalElement createMirror(boolean flat, double focalLength) {
        return new MirrorElement(flat, focalLength);
    }

    public static OpticalElement createMirror(boolean flat) {
        return createMirror(flat, 0.0);
    }

    public static OpticalElement createGrating(double period, double amplitude, boolean rectangular) {
        return new GratingElement(period, amplitude, rectangular);
    }

    public static OpticalElement createSlit(double width) {
        return new SlitElement(width);
    }

    public enum ElementType {
        FREE_SPACE,
        LENS,
        MIRROR,
        GRATING,
        SLIT
    }
}