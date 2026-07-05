package com.optics.simulation;

@FunctionalInterface
public interface Propagator {
    void propagate(ComplexField field, double distance);
}