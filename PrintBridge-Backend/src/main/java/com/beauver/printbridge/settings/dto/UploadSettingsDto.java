package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The upload limits the admin sets. The size may not exceed the server's ceiling; the service checks that. */
public record UploadSettingsDto(
        @NotNull(message = "Max files is required")
        @Min(value = 1, message = "Max files must be at least 1")
        @Max(value = 50, message = "Max files must be at most 50")
        Integer maxFiles,

        @NotNull(message = "Max size per file is required")
        @Min(value = 1, message = "Max size per file must be at least 1 MB")
        Integer maxFileSizeMb) {
}
