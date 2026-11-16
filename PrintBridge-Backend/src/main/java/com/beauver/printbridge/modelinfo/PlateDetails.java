package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelfile.analysis.FilamentUsage;

import java.util.List;

/**
 * One plate of a model file; sizes, grams and {@code estimated} are {@code null} for an empty plate.
 *
 * @param estimated  whether {@code grams} is an estimate from the plate's volume
 * @param sliceError why the slicer refused this plate; only filled in for the admin
 */
public record PlateDetails(
        int index,
        String name,
        Double width,
        Double depth,
        Double height,
        Double grams,
        Boolean estimated,
        Long printSeconds,
        Boolean fits,
        List<FilamentUsage> filaments,
        String sliceError) {
}
