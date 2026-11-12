package com.beauver.printbridge.modelinfo.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VectorsTest {

    @Test
    void tripleProductIsTheSpannedVolume() {
        assertEquals(1, Vectors.tripleProduct(1, 0, 0, 0, 1, 0, 0, 0, 1), 1e-9);
        assertEquals(24, Vectors.tripleProduct(2, 0, 0, 0, 3, 0, 0, 0, 4), 1e-9);
        assertEquals(-1, Vectors.tripleProduct(0, 1, 0, 1, 0, 0, 0, 0, 1), 1e-9);
    }

    @Test
    void tetrahedronIsASixthOfIt() {
        assertEquals(1.0 / 6, MeshAccumulator.signedTetrahedronVolume(1, 0, 0, 0, 1, 0, 0, 0, 1), 1e-9);
    }
}
