package com.beauver.printbridge.spoolman;

import java.util.List;

/**
 * A filament in Spoolman with what is left of it.
 *
 * @param name            optional in Spoolman
 * @param vendor          the vendor's name; optional
 * @param material        e.g. PLA; optional
 * @param color           {@code #RRGGBB}; {@code null} for multi-colour filaments or when not set
 * @param colors          the colours of a multi-colour filament ({@code #RRGGBB}); otherwise empty
 * @param gramsLeft       the weight left on its spools that aren't archived; 0 without such spools,
 *                        {@code null} when a spool's weight is unknown
 */
public record SpoolmanFilament(
        long id,
        String name,
        String vendor,
        String material,
        String color,
        List<String> colors,
        Double gramsLeft) {

    /** Whether less is left than {@code gramsNeeded}; a filament with nothing left never has enough. */
    public boolean notEnoughFor(Double gramsNeeded) {
        if (gramsLeft == null) {
            return false;
        }
        if (gramsLeft <= 0) {
            return true;
        }
        return gramsNeeded != null && gramsNeeded > gramsLeft;
    }
}
