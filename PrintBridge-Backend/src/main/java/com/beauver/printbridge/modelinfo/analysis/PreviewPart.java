package com.beauver.printbridge.modelinfo.analysis;

/**
 * One coloured mesh on a plate, placed on the bed (x/y on the bed, z up from it, in mm).
 *
 * @param plate     the plate it is on
 * @param color     "#RRGGBB" filament colour; {@code null} for the viewer's neutral colour
 * @param positions x, y, z per vertex
 * @param indices   three vertex indices per triangle
 */
public record PreviewPart(int plate, String color, float[] positions, int[] indices) {

    public int vertexCount() {
        return positions.length / 3;
    }

    public int triangleCount() {
        return indices.length / 3;
    }
}
