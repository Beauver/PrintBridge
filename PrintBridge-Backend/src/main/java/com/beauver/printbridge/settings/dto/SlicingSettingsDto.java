package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Server slicing on/off and how long one file may take. The printers are set up separately. */
public record SlicingSettingsDto(
        @NotNull Boolean enabled,
        @NotNull @Min(1) @Max(60) Integer timeoutMinutes) {
}
