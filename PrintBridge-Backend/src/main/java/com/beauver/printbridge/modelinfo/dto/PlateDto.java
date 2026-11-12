package com.beauver.printbridge.modelinfo.dto;

import java.util.List;

/**
 * One plate. Sizes are {@code null} for an empty plate.
 *
 * @param grams        exact weight from a sliced project, else {@code null}
 * @param printSeconds slicer's time estimate, else {@code null}
 * @param fits         whether it fits the build plate; {@code null} when no plate size is set or the plate is empty
 */
public record PlateDto(
        int index,
        String name,
        Double width,
        Double depth,
        Double height,
        Double grams,
        Long printSeconds,
        Boolean fits,
        List<FilamentDto> filaments) {
}
