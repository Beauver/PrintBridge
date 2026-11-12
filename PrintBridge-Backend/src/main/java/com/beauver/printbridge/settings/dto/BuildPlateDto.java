package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The printer's build volume in whole mm; {@code null} values until the admin sets it. */
public record BuildPlateDto(
        @NotNull @Min(1) @Max(2000) Integer x,
        @NotNull @Min(1) @Max(2000) Integer y,
        @NotNull @Min(1) @Max(2000) Integer z) {
}
