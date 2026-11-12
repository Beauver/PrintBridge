package com.beauver.printbridge.modelinfo.analysis;

/**
 * Affine transform as 3MF writes it: 12 numbers {@code m00 m01 m02 m10 m11 m12 m20 m21 m22 m30
 * m31 m32}, applied to row vectors ({@code p' = p × M}), so the last three are the translation.
 */
final class Transform {

    static final Transform IDENTITY = new Transform(new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0});

    private final double[] m;

    private Transform(double[] m) {
        this.m = m;
    }

    /** Parses the attribute value; a missing attribute means identity. */
    static Transform parse(String value) throws ModelAnalysisException {
        if (value == null || value.isBlank()) {
            return IDENTITY;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 12) {
            throw new ModelAnalysisException("Invalid transform: " + abbreviate(value));
        }
        double[] m = new double[12];
        try {
            for (int i = 0; i < 12; i++) {
                m[i] = Double.parseDouble(parts[i]);
                if (!Double.isFinite(m[i])) {
                    throw new ModelAnalysisException("Invalid transform: " + abbreviate(value));
                }
            }
        } catch (NumberFormatException e) {
            throw new ModelAnalysisException("Invalid transform: " + abbreviate(value), e);
        }
        return new Transform(m);
    }

    double x(double x, double y, double z) {
        return x * m[0] + y * m[3] + z * m[6] + m[9];
    }

    double y(double x, double y, double z) {
        return x * m[1] + y * m[4] + z * m[7] + m[10];
    }

    double z(double x, double y, double z) {
        return x * m[2] + y * m[5] + z * m[8] + m[11];
    }

    /** This transform followed by {@code next}. */
    Transform then(Transform next) {
        double[] a = m;
        double[] b = next.m;
        double[] c = new double[12];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 3; column++) {
                double sum = 0;
                for (int k = 0; k < 3; k++) {
                    sum += a[row * 3 + k] * b[k * 3 + column];
                }
                if (row == 3) {
                    sum += b[9 + column];
                }
                c[row * 3 + column] = sum;
            }
        }
        return new Transform(c);
    }

    /** How the transform scales volumes (negative when it mirrors). */
    double determinant() {
        // The 3×3 part without the translation: where the x, y and z axes end up.
        double xAxisX = m[0];
        double xAxisY = m[1];
        double xAxisZ = m[2];
        double yAxisX = m[3];
        double yAxisY = m[4];
        double yAxisZ = m[5];
        double zAxisX = m[6];
        double zAxisY = m[7];
        double zAxisZ = m[8];
        // The volume of the box the transformed axes span: 1 for a pure rotation, 8 for scale 2.
        return Vectors.tripleProduct(xAxisX, xAxisY, xAxisZ, yAxisX, yAxisY, yAxisZ, zAxisX, zAxisY, zAxisZ);
    }

    private static String abbreviate(String value) {
        if (value.length() <= 60) {
            return value;
        }
        return value.substring(0, 60) + "…";
    }
}
