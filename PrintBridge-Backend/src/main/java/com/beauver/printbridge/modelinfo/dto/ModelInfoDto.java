package com.beauver.printbridge.modelinfo.dto;

import com.beauver.printbridge.modelinfo.ModelInfoStatus;
import com.beauver.printbridge.modelinfo.analysis.ModelSource;

import java.util.List;

/**
 * What is known about a model file. Unless the status is ANALYZED, only {@code status} and
 * {@code slicing} are set and {@code plates} is empty.
 *
 * @param totalGrams   sum over the plates with objects (exact and estimated)
 * @param weight       how much of {@code totalGrams} is exact
 * @param printSeconds total print time; only when every plate with objects has one
 * @param fits         whether every plate fits on {@code fitPrinter}; {@code null} without printers
 * @param fitPrinter   the printer the fit check is for: the one it was sliced for, else the default printer
 */
public record ModelInfoDto(
        ModelInfoStatus status,
        ModelSource source,
        Double volumeCm3,
        Double totalGrams,
        WeightKind weight,
        Long printSeconds,
        Boolean fits,
        String fitPrinter,
        List<PlateDto> plates,
        SlicingInfoDto slicing) {

    public static ModelInfoDto withStatus(ModelInfoStatus status, SlicingInfoDto slicing) {
        return new ModelInfoDto(status, null, null, null, null, null, null, null, List.of(), slicing);
    }
}
