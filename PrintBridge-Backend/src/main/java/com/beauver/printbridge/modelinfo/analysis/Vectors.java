package com.beauver.printbridge.modelinfo.analysis;

/** Small 3D vector maths shared by the volume and transform calculations. */
final class Vectors {

    private Vectors() {
    }

    /**
     * The scalar triple product {@code a · (b × c)}: the signed volume of the parallelepiped
     * spanned by the three vectors. Its sign flips when the vectors are mirrored (their order goes
     * from counter-clockwise to clockwise).
     * <ul>
     *   <li>For the corners of a triangle it is six times the signed volume of the tetrahedron
     *       between the triangle and the origin (used for mesh volumes).</li>
     *   <li>For the rows of a 3×3 matrix it is the determinant: how much the matrix scales
     *       volumes (used for transforms).</li>
     * </ul>
     */
    static double tripleProduct(double ax, double ay, double az,
                                double bx, double by, double bz,
                                double cx, double cy, double cz) {
        // b × c: a vector perpendicular to b and c, as long as the area of their parallelogram
        double crossX = by * cz - bz * cy;
        double crossY = bz * cx - bx * cz;
        double crossZ = bx * cy - by * cx;
        // a · (b × c): how far a reaches along that perpendicular, times that area
        return ax * crossX + ay * crossY + az * crossZ;
    }
}
