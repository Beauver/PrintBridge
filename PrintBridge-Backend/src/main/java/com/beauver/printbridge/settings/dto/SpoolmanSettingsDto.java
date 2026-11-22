package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The Spoolman settings (#11).
 *
 * @param enabled          read filaments from Spoolman
 * @param colorSelection   requesters choose a filament from Spoolman per colour; needs {@code enabled}
 * @param prices           the price estimate uses the chosen filaments' Spoolman prices (#72); needs
 *                         {@code enabled}
 * @param autoMatch        colours of new projects get the closest filament automatically (#76); needs
 *                         {@code colorSelection}
 * @param matchTolerance   the largest colour difference (CIEDE2000 ΔE) automatic matching accepts
 * @param notEnoughMessage shown when a request needs more of a chosen filament than is left. Returned
 *                         with the default filled in; saving it empty (or the default) goes back to the
 *                         default text
 * @param noMatchMessage   shown for colours without a close enough filament; like {@code notEnoughMessage}
 */
public record SpoolmanSettingsDto(
        @NotNull Boolean enabled,
        @NotNull Boolean colorSelection,
        @NotNull Boolean prices,
        @NotNull Boolean autoMatch,
        @NotNull(message = "Tolerance is required")
        @Min(value = 1, message = "Tolerance must be at least 1")
        @Max(value = 100, message = "Tolerance must be at most 100")
        Integer matchTolerance,
        @Size(max = 300, message = "The warning text must be at most 300 characters")
        String notEnoughMessage,
        @Size(max = 300, message = "The no-match text must be at most 300 characters")
        String noMatchMessage) {
}
