package com.beauver.printbridge.modelinfo.analysis;

/**
 * Bounding box and volume of a triangle mesh, fed one triangle at a time. The volume is the sum
 * of the signed tetrahedra between each triangle and the origin, which is exact for a closed
 * mesh; its sign depends on the winding, so callers use the absolute value.
 */
final class MeshAccumulator {

    private final Bounds bounds = new Bounds();
    private double signedVolume;
    private long triangles;

    void addTriangle(double x1, double y1, double z1,
                     double x2, double y2, double z2,
                     double x3, double y3, double z3) throws ModelAnalysisException {
        if (!Double.isFinite(x1 + y1 + z1 + x2 + y2 + z2 + x3 + y3 + z3)) {
            throw new ModelAnalysisException("The model contains invalid coordinates");
        }
        bounds.add(x1, y1, z1);
        bounds.add(x2, y2, z2);
        bounds.add(x3, y3, z3);
        signedVolume += signedTetrahedronVolume(x1, y1, z1, x2, y2, z2, x3, y3, z3);
        triangles++;
    }

    static double signedTetrahedronVolume(double x1, double y1, double z1,
                                          double x2, double y2, double z2,
                                          double x3, double y3, double z3) {
        return (x1 * (y2 * z3 - z2 * y3) - y1 * (x2 * z3 - z2 * x3) + z1 * (x2 * y3 - y2 * x3)) / 6.0;
    }

    Bounds bounds() {
        return bounds;
    }

    double volume() {
        return Math.abs(signedVolume);
    }

    long triangles() {
        return triangles;
    }
}
