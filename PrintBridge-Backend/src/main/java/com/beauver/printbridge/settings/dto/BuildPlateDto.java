package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** A printer's build volume in whole mm. */
public record BuildPlateDto(
        @NotNull @Min(1) @Max(2000) Integer x,
        @NotNull @Min(1) @Max(2000) Integer y,
        @NotNull @Min(1) @Max(2000) Integer z) {
}
