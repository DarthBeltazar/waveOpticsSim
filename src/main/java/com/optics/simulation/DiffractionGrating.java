package com.optics.simulation;

public record DiffractionGrating(double period, double amplitude, boolean rectangular) {

    public void apply(ComplexField field) {
        if (rectangular) {
            field.applyPhaseMask((x, y) -> {
                double phase = 0.0;
                if (Math.sin(2.0 * Math.PI * x / period) >= 0) phase += amplitude;
                if (Math.sin(2.0 * Math.PI * y / period) >= 0) phase += amplitude;
                return phase;
            });
        } else {
            field.applyPhaseMask((x, y) -> amplitude * (Math.sin(2.0 * Math.PI * x / period)
                    + Math.sin(2.0 * Math.PI * y / period)));
        }
    }
}