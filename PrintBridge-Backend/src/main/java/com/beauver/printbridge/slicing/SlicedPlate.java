package com.beauver.printbridge.slicing;

import com.beauver.printbridge.modelinfo.analysis.FilamentUsage;

import java.util.List;

/**
 * Server slicing result of one plate: the numbers, or OrcaSlicer's reason for refusing it.
 *
 * @param seconds estimated print time
 * @param error   why OrcaSlicer refused the plate; {@code null} when sliced
 */
public record SlicedPlate(int index, Double grams, Long seconds, List<FilamentUsage> filaments, String error) {
}
