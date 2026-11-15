package com.beauver.printbridge.modelfile.geometry;

import com.beauver.printbridge.modelfile.analysis.Dimensions;

/** Axis-aligned bounding box that grows as points are added. */
public final class Bounds {

    private double minX = Double.POSITIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double minZ = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;
    private double maxZ = Double.NEGATIVE_INFINITY;

    public void add(double x, double y, double z) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }

    public void add(Bounds other) {
        if (!other.isEmpty()) {
            add(other.minX, other.minY, other.minZ);
            add(other.maxX, other.maxY, other.maxZ);
        }
    }

    public double minX() {
        return minX;
    }

    public double minY() {
        return minY;
    }

    public double minZ() {
        return minZ;
    }

    public double maxX() {
        return maxX;
    }

    public double maxY() {
        return maxY;
    }

    public boolean isEmpty() {
        return minX > maxX;
    }

    /** The size, or {@code null} when nothing was added. */
    public Dimensions dimensions() {
        if (isEmpty()) {
            return null;
        }
        return Dimensions.of(maxX - minX, maxY - minY, maxZ - minZ);
    }
}
