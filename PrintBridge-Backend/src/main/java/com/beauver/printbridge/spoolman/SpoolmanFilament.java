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
 * @param price           what a spool of it costs (Spoolman's currency, taken as EUR); optional (#72)
 * @param weight          the net filament weight of a spool in grams; optional
 * @param density         g/cm³; optional
 */
public record SpoolmanFilament(
        long id,
        String name,
        String vendor,
        String material,
        String color,
        List<String> colors,
        Double gramsLeft,
        Double price,
        Double weight,
        Double density) {

    /** Price per gram (spool price ÷ net weight); {@code null} when either is unknown or not positive. */
    public Double pricePerGram() {
        if (price == null || weight == null || price < 0 || weight <= 0) {
            return null;
        }
        return price / weight;
    }

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
