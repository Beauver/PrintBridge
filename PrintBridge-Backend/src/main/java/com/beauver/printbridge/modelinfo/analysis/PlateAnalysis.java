package com.beauver.printbridge.modelinfo.analysis;

import java.util.List;

/**
 * One plate (or the whole model for an STL / plain 3MF).
 *
 * @param index        1-based plate number as the slicer shows it
 * @param name         plate name from the slicer; {@code null} when unnamed
 * @param size         bounding box of what is on the plate; {@code null} for an empty plate
 * @param grams        exact filament weight from a sliced project; {@code null} when not sliced
 * @param printSeconds slicer's print time estimate; {@code null} when not sliced
 */
public record PlateAnalysis(
        int index,
        String name,
        Dimensions size,
        Double grams,
        Long printSeconds,
        List<FilamentUsage> filaments) {
}
