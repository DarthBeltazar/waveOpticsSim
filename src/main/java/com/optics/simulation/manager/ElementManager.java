package com.optics.simulation.manager;

import com.optics.simulation.model.OpticalElement;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ElementManager {
    private final ObservableList<OpticalElement> elements = FXCollections.observableArrayList();

    public ObservableList<OpticalElement> getElements() {
        return elements;
    }

    public void add(OpticalElement element) {
        elements.add(element);
    }

    public void remove(OpticalElement element) {
        elements.remove(element);
    }

    public void clear() {
        elements.clear();
    }

    public void set(int index, OpticalElement element) {
        elements.set(index, element);
    }

    public int size() {
        return elements.size();
    }
}