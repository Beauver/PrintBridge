package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelfile.analysis.ModelSource;

import java.util.List;

/**
 * What is known about a model file, worked out by {@link ModelDetailsCalculator}: the stored
 * analysis combined with server slicing and the fit check. Unless ANALYZED only {@code status}
 * and {@code slicing} are set and {@code plates} is empty.
 *
 * @param totalGrams   sum over the plates with objects (exact and estimated)
 * @param printSeconds only when every plate with objects has a time
 * @param fits         whether every plate fits {@code fitPrinter}; {@code null} without printers
 * @param fitPrinter   the printer the fit check is for
 */
public record ModelDetails(
        ModelInfoStatus status,
        ModelSource source,
        Double volumeCm3,
        Double totalGrams,
        WeightKind weight,
        Long printSeconds,
        Boolean fits,
        String fitPrinter,
        List<PlateDetails> plates,
        SlicingDetails slicing) {

    public static ModelDetails withStatus(ModelInfoStatus status, SlicingDetails slicing) {
        return new ModelDetails(status, null, null, null, null, null, null, null, List.of(), slicing);
    }
}
