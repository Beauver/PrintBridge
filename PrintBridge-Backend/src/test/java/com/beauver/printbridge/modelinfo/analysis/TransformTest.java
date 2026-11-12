package com.beauver.printbridge.modelinfo.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformTest {

    @Test
    void appliesRowVectorConvention() throws Exception {
        // 90° around Z (x -> y), then moved by (1, 2, 3)
        Transform t = Transform.parse("0 1 0 -1 0 0 0 0 1 1 2 3");

        assertEquals(1, t.x(1, 0, 0), 1e-9);
        assertEquals(3, t.y(1, 0, 0), 1e-9);
        assertEquals(3, t.z(1, 0, 0), 1e-9);
    }

    @Test
    void composesInOrder() throws Exception {
        Transform scale = Transform.parse("2 0 0 0 2 0 0 0 2 0 0 0");
        Transform move = Transform.parse("1 0 0 0 1 0 0 0 1 10 0 0");

        assertEquals(12, scale.then(move).x(1, 0, 0), 1e-9);
        assertEquals(22, move.then(scale).x(1, 0, 0), 1e-9);
        assertEquals(8, scale.then(move).determinant(), 1e-9);
    }

    @Test
    void missingTransformIsIdentity() throws Exception {
        assertSame(Transform.IDENTITY, Transform.parse(null));
        assertSame(Transform.IDENTITY, Transform.parse(" "));
        assertEquals(1, Transform.IDENTITY.determinant(), 1e-9);
    }

    @Test
    void abbreviatesLongInvalidValues() {
        ModelAnalysisException e = assertThrows(ModelAnalysisException.class, () -> Transform.parse("1 ".repeat(40)));

        assertTrue(e.getMessage().endsWith("…"));
    }
}
