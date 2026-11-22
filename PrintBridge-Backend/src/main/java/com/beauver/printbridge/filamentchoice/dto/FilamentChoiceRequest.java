package com.beauver.printbridge.filamentchoice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Choose a Spoolman filament for one colour of a plate, as the model info lists it, or for the
 * whole item ({@code wholeItem} true; then {@code plateIndex}, {@code originalType} and
 * {@code originalColor} are left out).
 */
public record FilamentChoiceRequest(
        @NotNull(message = "Say whether this is for the whole item")
        Boolean wholeItem,
        @Min(value = 0, message = "Plate must be 0 or more")
        Integer plateIndex,
        @Size(max = 100, message = "Type is too long")
        String originalType,
        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Colour must be #RRGGBB")
        String originalColor,
        @NotNull(message = "Choose a filament")
        Long spoolmanFilamentId) {
}
