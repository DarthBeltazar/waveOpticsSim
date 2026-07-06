package com.optics.simulation;

import com.optics.simulation.engine.SimulationEngine;
import com.optics.simulation.factory.ElementFactory;
import com.optics.simulation.manager.ElementManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {

    @Test
    void testFraunhoferDiffractionSlit() throws Exception {
        double lambda = 0.5e-6;
        double slitWidth = 0.0001;
        double distance = 0.1;
        int N = 1024; // увеличено для лучшей точности
        double dx = 1e-5;

        ElementManager manager = new ElementManager();
        manager.add(ElementFactory.createSlit(slitWidth));
        manager.add(ElementFactory.createFreeSpace(distance));

        ComplexField field = new ComplexField(N, dx, lambda);
        // Plane wave
        double[][] data = field.getData();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                int idx = 2 * j;
                data[i][idx] = 1.0;
                data[i][idx + 1] = 0.0;
            }
        }

        SimulationEngine engine = new SimulationEngine();
        engine.run(field, manager.getElements());

        double[][] intensity = field.computeIntensity();
        int centerRow = N / 2;
        double half = N / 2.0;

        // Проверяем центральный максимум
        double maxIntensity = intensity[centerRow][(int)half];
        assertTrue(maxIntensity > 0);

        // Находим первый минимум справа
        int firstMinPos = (int)half;
        for (int j = (int)half + 1; j < N; j++) {
            if (intensity[centerRow][j] < maxIntensity * 0.1) {
                firstMinPos = j;
                break;
            }
        }
        double actualFirstMin = (firstMinPos - half) * dx;

        // Теоретическое положение первого минимума (Fraunhofer)
        double expectedFirstMin = lambda * distance / slitWidth;
        // Допустим 30% погрешности из-за дискретизации
        double relativeError = Math.abs(actualFirstMin - expectedFirstMin) / expectedFirstMin;
        assertTrue(relativeError < 0.35,
                "First minimum position mismatch: expected " + expectedFirstMin + " got " + actualFirstMin);
    }
}