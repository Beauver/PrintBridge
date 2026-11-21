package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The Spoolman switches (#11).
 *
 * @param enabled        read filaments from Spoolman
 * @param colorSelection requesters choose a filament from Spoolman per colour; needs {@code enabled}
 */
public record SpoolmanSettingsDto(@NotNull Boolean enabled, @NotNull Boolean colorSelection) {
}
