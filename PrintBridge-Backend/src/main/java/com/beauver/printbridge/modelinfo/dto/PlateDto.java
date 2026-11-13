package com.beauver.printbridge.modelinfo.dto;

import java.util.List;

/**
 * One plate. Sizes, grams and {@code estimated} are {@code null} for an empty plate.
 *
 * @param grams        exact (server or file slice) or, when {@code estimated}, from the plate's volume
 * @param printSeconds only known when sliced
 * @param fits         whether it fits the build plate; {@code null} when no plate size is set or the plate is empty
 * @param sliceError   why OrcaSlicer refused this plate; only for the admin
 */
public record PlateDto(
        int index,
        String name,
        Double width,
        Double depth,
        Double height,
        Double grams,
        Boolean estimated,
        Long printSeconds,
        Boolean fits,
        List<FilamentDto> filaments,
        String sliceError) {
}
