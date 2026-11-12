package com.beauver.printbridge.modelinfo.dto;

import com.beauver.printbridge.modelinfo.ModelInfoStatus;
import com.beauver.printbridge.modelinfo.analysis.ModelSource;

import java.util.List;

/**
 * What is known about a model file. Everything but {@code status} is {@code null} (and
 * {@code plates} empty) unless the status is ANALYZED.
 *
 * @param estimatedGrams rough weight of the whole model, from its volume
 * @param slicedGrams    exact total from the slicer; only when every plate with objects is sliced
 * @param printSeconds   total print time; only when every plate with objects is sliced
 * @param fits           whether every plate fits the build plate; {@code null} when no plate size is set
 */
public record ModelInfoDto(
        ModelInfoStatus status,
        ModelSource source,
        Double volumeCm3,
        Double estimatedGrams,
        Double slicedGrams,
        Long printSeconds,
        Boolean fits,
        List<PlateDto> plates) {

    public static ModelInfoDto withStatus(ModelInfoStatus status) {
        return new ModelInfoDto(status, null, null, null, null, null, null, List.of());
    }
}
