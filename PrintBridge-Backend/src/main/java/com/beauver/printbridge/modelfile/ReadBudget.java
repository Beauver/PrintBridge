package com.beauver.printbridge.modelfile;

import com.beauver.printbridge.modelfile.exception.ModelAnalysisException;
import com.beauver.printbridge.modelfile.exception.ModelTooDetailedException;

/**
 * Limits for one analysis, so a hostile or huge upload can't exhaust memory or run for ages. A
 * 3MF is a ZIP: a small upload can decompress to gigabytes (a "zip bomb"), and the vertices of
 * every object are kept in memory while placing them.
 */
public final class ReadBudget {

    /** Uncompressed bytes read from a 3MF, all entries together. */
    public static final long MAX_BYTES = 1024L * 1024 * 1024;
    /** Vertices kept in memory (3 floats each, so 60 MB at the limit). */
    public static final long MAX_VERTICES = 5_000_000;
    /** Triangles kept in memory for a preview (3 ints each, so 60 MB at the limit). */
    public static final long MAX_TRIANGLES = 5_000_000;

    /**
     * Vertices placed on the build, all placements together. A 3MF can place one mesh any number
     * of times, with build items or with components inside components, so a file of a few hundred
     * bytes can ask for billions of placements.
     */
    public static final long MAX_PLACED_VERTICES = 50_000_000;
    /** What placing an object costs on top of its vertices, so objects without a mesh aren't free. */
    public static final long PLACEMENT_COST = 16;
    /** Uncompressed bytes of one XML metadata part (plates, slicing results, relationships). */
    public static final long MAX_METADATA_BYTES = 16L * 1024 * 1024;
    /**
     * Uncompressed bytes of the project settings. Smaller than the other metadata: this JSON is
     * read into memory as a tree, which takes many times its size. Real ones are tens of kB.
     */
    public static final long MAX_SETTINGS_BYTES = 2L * 1024 * 1024;

    private final long maxBytes;
    private final long maxVertices;
    private final long maxTriangles;
    private final long maxPlacedVertices;
    private long bytes;
    private long vertices;
    private long triangles;
    private long placedVertices;

    public ReadBudget() {
        this(MAX_BYTES, MAX_VERTICES, MAX_TRIANGLES);
    }

    public ReadBudget(long maxBytes, long maxVertices) {
        this(maxBytes, maxVertices, MAX_TRIANGLES);
    }

    public ReadBudget(long maxBytes, long maxVertices, long maxTriangles) {
        this(maxBytes, maxVertices, maxTriangles, MAX_PLACED_VERTICES);
    }

    public ReadBudget(long maxBytes, long maxVertices, long maxTriangles, long maxPlacedVertices) {
        this.maxBytes = maxBytes;
        this.maxVertices = maxVertices;
        this.maxTriangles = maxTriangles;
        this.maxPlacedVertices = maxPlacedVertices;
    }

    /** The default limits, with another limit on placed vertices. */
    public static ReadBudget placing(long maxPlacedVertices) {
        return new ReadBudget(MAX_BYTES, MAX_VERTICES, MAX_TRIANGLES, maxPlacedVertices);
    }

    public void addBytes(long count) throws ModelAnalysisException {
        bytes += count;
        if (bytes > maxBytes) {
            throw new ModelAnalysisException("The file unpacks to more than " + maxBytes + " bytes");
        }
    }

    public void addVertex() throws ModelAnalysisException {
        vertices++;
        if (vertices > maxVertices) {
            throw new ModelTooDetailedException("The model has more than " + maxVertices + " vertices");
        }
    }

    public void addTriangle() throws ModelAnalysisException {
        triangles++;
        if (triangles > maxTriangles) {
            throw new ModelTooDetailedException("The model has more than " + maxTriangles + " triangles");
        }
    }

    /** Charges one placement of an object: its vertices (0 without a mesh) plus {@link #PLACEMENT_COST}. */
    public void addPlacement(long vertexCount) throws ModelAnalysisException {
        placedVertices += vertexCount + PLACEMENT_COST;
        if (placedVertices > maxPlacedVertices) {
            throw new ModelTooDetailedException("The model places more than " + maxPlacedVertices + " vertices");
        }
    }
}
