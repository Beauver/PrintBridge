package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Application settings; the build plate size (mm) is {@code null} until the admin sets it. */
public record SettingsDto(
        @NotNull @Min(1) @Max(2000) Integer buildPlateX,
        @NotNull @Min(1) @Max(2000) Integer buildPlateY,
        @NotNull @Min(1) @Max(2000) Integer buildPlateZ) {
}
