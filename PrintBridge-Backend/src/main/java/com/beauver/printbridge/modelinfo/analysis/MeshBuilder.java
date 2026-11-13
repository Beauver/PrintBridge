package com.beauver.printbridge.modelinfo.analysis;

import java.util.Arrays;

/**
 * Builds an indexed mesh from triangles given by their corners, sharing corners that are exactly
 * the same (as they are in an STL). An open-addressing table keyed on the coordinates' bits keeps
 * this to a few arrays, without an object per vertex.
 */
final class MeshBuilder implements TriangleSink {

    private static final int EMPTY = -1;

    private float[] positions = new float[3 * 1024];
    private int vertexCount;
    private int[] indices = new int[3 * 1024];
    private int indexCount;
    /** Vertex index per slot, or {@link #EMPTY}; kept at most half full. */
    private int[] table = emptyTable(2048);
    private final long maxTriangles;

    MeshBuilder(long maxTriangles) {
        this.maxTriangles = maxTriangles;
    }

    @Override
    public void addTriangle(double x1, double y1, double z1,
                            double x2, double y2, double z2,
                            double x3, double y3, double z3) throws ModelAnalysisException {
        if (!Double.isFinite(x1 + y1 + z1 + x2 + y2 + z2 + x3 + y3 + z3)) {
            throw new ModelAnalysisException("The model contains invalid coordinates");
        }
        if (indexCount / 3 >= maxTriangles) {
            throw new ModelTooDetailedException("The model has more than " + maxTriangles + " triangles");
        }
        addIndex(vertex((float) x1, (float) y1, (float) z1));
        addIndex(vertex((float) x2, (float) y2, (float) z2));
        addIndex(vertex((float) x3, (float) y3, (float) z3));
    }

    int triangleCount() {
        return indexCount / 3;
    }

    float[] positions() {
        return Arrays.copyOf(positions, vertexCount * 3);
    }

    int[] indices() {
        return Arrays.copyOf(indices, indexCount);
    }

    private int vertex(float x, float y, float z) {
        int mask = table.length - 1;
        int slot = hash(x, y, z) & mask;
        while (table[slot] != EMPTY) {
            int existing = table[slot] * 3;
            if (positions[existing] == x && positions[existing + 1] == y && positions[existing + 2] == z) {
                return table[slot];
            }
            slot = (slot + 1) & mask;
        }
        if (vertexCount * 3 + 3 > positions.length) {
            positions = Arrays.copyOf(positions, positions.length * 2);
        }
        int index = vertexCount++;
        positions[index * 3] = x;
        positions[index * 3 + 1] = y;
        positions[index * 3 + 2] = z;
        table[slot] = index;
        if (vertexCount * 2 > table.length) {
            grow();
        }
        return index;
    }

    private void grow() {
        table = emptyTable(table.length * 2);
        int mask = table.length - 1;
        for (int index = 0; index < vertexCount; index++) {
            int slot = hash(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]) & mask;
            while (table[slot] != EMPTY) {
                slot = (slot + 1) & mask;
            }
            table[slot] = index;
        }
    }

    private void addIndex(int index) {
        if (indexCount + 1 > indices.length) {
            indices = Arrays.copyOf(indices, indices.length * 2);
        }
        indices[indexCount++] = index;
    }

    private static int hash(float x, float y, float z) {
        int hash = Float.floatToIntBits(x);
        hash = hash * 31 + Float.floatToIntBits(y);
        hash = hash * 31 + Float.floatToIntBits(z);
        // Spread the bits, so nearby coordinates don't land in neighbouring slots.
        return hash ^ (hash >>> 16);
    }

    private static int[] emptyTable(int size) {
        int[] table = new int[size];
        Arrays.fill(table, EMPTY);
        return table;
    }
}
