package com.optics.simulation.ui;

import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

public class Surface3D {
    public static Group createSurface(double[][] data, double dx, int subsample, boolean showPhase) {
        int rows = data.length;
        int cols = data[0].length;
        int step = Math.max(1, subsample);
        int r = rows / step;
        int c = cols / step;

        if (r < 2 || c < 2) {
            System.err.println("Not enough data points. r=" + r + ", c=" + c);
            return new Group();
        }

        double xScale = 200.0 / (c - 1);
        double yScale = 200.0 / (r - 1);
        double zScale = 50.0;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < rows; i += step) {
            for (int j = 0; j < cols; j += step) {
                double val = data[i][j];
                if (!Double.isFinite(val)) continue;
                if (val < min) min = val;
                if (val > max) max = val;
            }
        }
        double range = max - min;
        if (range < 1e-12) range = 1.0;

        int numVertices = r * c;
        float[] points = new float[numVertices * 3];
        float[] texCoords = new float[numVertices * 2];
        int[] faces = new int[(r - 1) * (c - 1) * 12];

        int idx = 0;
        for (int i = 0; i < r; i++) {
            int origI = i * step;
            for (int j = 0; j < c; j++) {
                int origJ = j * step;
                double x = (j - c / 2.0) * xScale;
                double y = (i - r / 2.0) * yScale;
                double val = data[origI][origJ];
                double z = (val - min) / range * zScale;
                points[idx * 3] = (float) x;
                points[idx * 3 + 1] = (float) y;
                points[idx * 3 + 2] = (float) z;
                texCoords[idx * 2] = (float) j / (c - 1);
                texCoords[idx * 2 + 1] = (float) i / (r - 1);
                idx++;
            }
        }

        int faceIdx = 0;
        for (int i = 0; i < r - 1; i++) {
            for (int j = 0; j < c - 1; j++) {
                int p00 = i * c + j;
                int p01 = i * c + j + 1;
                int p10 = (i + 1) * c + j;
                int p11 = (i + 1) * c + j + 1;

                faces[faceIdx++] = p00;
                faces[faceIdx++] = p00;
                faces[faceIdx++] = p01;
                faces[faceIdx++] = p01;
                faces[faceIdx++] = p11;
                faces[faceIdx++] = p11;
                faces[faceIdx++] = p00;
                faces[faceIdx++] = p00;
                faces[faceIdx++] = p11;
                faces[faceIdx++] = p11;
                faces[faceIdx++] = p10;
                faces[faceIdx++] = p10;
            }
        }

        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(points);
        mesh.getTexCoords().setAll(texCoords);
        mesh.getFaces().setAll(faces);

        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(Color.rgb(0, 200, 255)); // яркий голубой
        material.setSpecularColor(Color.WHITE);
        material.setSpecularPower(32.0);

        MeshView meshView = new MeshView(mesh);
        meshView.setMaterial(material);
        meshView.setDrawMode(DrawMode.FILL);
        meshView.setCullFace(CullFace.NONE);

        meshView.setDepthTest(DepthTest.ENABLE);


        return new Group(meshView);
    }
}