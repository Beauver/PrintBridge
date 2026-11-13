package com.beauver.printbridge.modelinfo.analysis;

/** Axis-aligned bounding box that grows as points are added. */
final class Bounds {

    private double minX = Double.POSITIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double minZ = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;
    private double maxZ = Double.NEGATIVE_INFINITY;

    void add(double x, double y, double z) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }

    void add(Bounds other) {
        if (!other.isEmpty()) {
            add(other.minX, other.minY, other.minZ);
            add(other.maxX, other.maxY, other.maxZ);
        }
    }

    double minX() {
        return minX;
    }

    double minY() {
        return minY;
    }

    double minZ() {
        return minZ;
    }

    double maxX() {
        return maxX;
    }

    double maxY() {
        return maxY;
    }

    boolean isEmpty() {
        return minX > maxX;
    }

    /** The size, or {@code null} when nothing was added. */
    Dimensions dimensions() {
        if (isEmpty()) {
            return null;
        }
        return Dimensions.of(maxX - minX, maxY - minY, maxZ - minZ);
    }
}
