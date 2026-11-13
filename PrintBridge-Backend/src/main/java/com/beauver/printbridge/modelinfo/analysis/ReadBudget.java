package com.beauver.printbridge.modelinfo.analysis;

/**
 * Limits for one analysis, so a hostile or huge upload can't exhaust memory or run for ages. A
 * 3MF is a ZIP: a small upload can decompress to gigabytes (a "zip bomb"), and the vertices of
 * every object are kept in memory while placing them.
 */
final class ReadBudget {

    /** Uncompressed bytes read from a 3MF, all entries together. */
    static final long MAX_BYTES = 1024L * 1024 * 1024;
    /** Vertices kept in memory (3 floats each, so 60 MB at the limit). */
    static final long MAX_VERTICES = 5_000_000;
    /** Triangles kept in memory for a preview (3 ints each, so 60 MB at the limit). */
    static final long MAX_TRIANGLES = 5_000_000;

    private final long maxBytes;
    private final long maxVertices;
    private final long maxTriangles;
    private long bytes;
    private long vertices;
    private long triangles;

    ReadBudget() {
        this(MAX_BYTES, MAX_VERTICES, MAX_TRIANGLES);
    }

    ReadBudget(long maxBytes, long maxVertices) {
        this(maxBytes, maxVertices, MAX_TRIANGLES);
    }

    ReadBudget(long maxBytes, long maxVertices, long maxTriangles) {
        this.maxBytes = maxBytes;
        this.maxVertices = maxVertices;
        this.maxTriangles = maxTriangles;
    }

    void addBytes(long count) throws ModelAnalysisException {
        bytes += count;
        if (bytes > maxBytes) {
            throw new ModelAnalysisException("The file unpacks to more than " + maxBytes + " bytes");
        }
    }

    void addVertex() throws ModelAnalysisException {
        vertices++;
        if (vertices > maxVertices) {
            throw new ModelTooDetailedException("The model has more than " + maxVertices + " vertices");
        }
    }

    void addTriangle() throws ModelAnalysisException {
        triangles++;
        if (triangles > maxTriangles) {
            throw new ModelTooDetailedException("The model has more than " + maxTriangles + " triangles");
        }
    }
}
