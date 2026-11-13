package com.beauver.printbridge.modelinfo.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeshBuilderTest {

    @Test
    void sharesIdenticalCorners() throws Exception {
        MeshBuilder builder = new MeshBuilder(10);
        builder.addTriangle(0, 0, 0, 1, 0, 0, 0, 1, 0);
        builder.addTriangle(1, 0, 0, 1, 1, 0, 0, 1, 0);

        assertEquals(2, builder.triangleCount());
        assertArrayEquals(new int[]{0, 1, 2, 1, 3, 2}, builder.indices());
        assertArrayEquals(new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0}, builder.positions());
    }

    @Test
    void growsPastItsFirstArrays() throws Exception {
        MeshBuilder builder = new MeshBuilder(10_000);
        for (int i = 0; i < 3000; i++) {
            builder.addTriangle(i, 0, 0, i, 1, 0, i, 0, 1);
        }

        assertEquals(3000, builder.triangleCount());
        assertEquals(9000, builder.positions().length / 3);
        assertEquals(9000, builder.indices().length);
        // Still finds corners after the table grew.
        builder.addTriangle(5, 0, 0, 5, 1, 0, 5, 0, 1);
        assertEquals(9000, builder.positions().length / 3);
    }

    @Test
    void refusesInvalidCoordinatesAndTooManyTriangles() throws Exception {
        MeshBuilder builder = new MeshBuilder(1);
        assertThrows(ModelAnalysisException.class,
                () -> builder.addTriangle(Double.NaN, 0, 0, 1, 0, 0, 0, 1, 0));
        builder.addTriangle(0, 0, 0, 1, 0, 0, 0, 1, 0);
        assertThrows(ModelTooDetailedException.class,
                () -> builder.addTriangle(0, 0, 0, 1, 0, 0, 0, 1, 0));
    }
}
