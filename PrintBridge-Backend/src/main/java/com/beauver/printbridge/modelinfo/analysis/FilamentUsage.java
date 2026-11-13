package com.beauver.printbridge.modelinfo.analysis;

/**
 * A filament used on a plate.
 *
 * @param type  material as the slicer names it (e.g. PLA); may be {@code null}
 * @param color {@code #RRGGBB}, or {@code null} when unknown
 * @param grams exact usage from a sliced project; {@code null} when not sliced
 */
public record FilamentUsage(String type, String color, Double grams) {

    /** A filament from outside data (e.g. the slicer service), with text and colour cleaned. */
    public static FilamentUsage cleaned(String type, String color, Double grams) {
        return new FilamentUsage(SlicerText.clean(type), SlicerText.color(color), grams);
    }
}
