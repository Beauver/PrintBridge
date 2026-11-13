package com.beauver.printbridge.modelinfo.analysis;

/**
 * A plate of a preview, also when it is empty.
 *
 * @param index 1-based plate number as the slicer shows it
 * @param name  plate name from the slicer; {@code null} when unnamed
 */
public record PreviewPlate(int index, String name) {
}
