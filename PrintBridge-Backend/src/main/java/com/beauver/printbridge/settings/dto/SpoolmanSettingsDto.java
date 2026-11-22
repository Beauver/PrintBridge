package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The Spoolman settings (#11).
 *
 * @param enabled          read filaments from Spoolman
 * @param colorSelection   requesters choose a filament from Spoolman per colour; needs {@code enabled}
 * @param notEnoughMessage shown when a request needs more of a chosen filament than is left. Returned
 *                         with the default filled in; saving it empty (or the default) goes back to the
 *                         default text
 */
public record SpoolmanSettingsDto(
        @NotNull Boolean enabled,
        @NotNull Boolean colorSelection,
        @Size(max = 300, message = "The warning text must be at most 300 characters")
        String notEnoughMessage) {
}
