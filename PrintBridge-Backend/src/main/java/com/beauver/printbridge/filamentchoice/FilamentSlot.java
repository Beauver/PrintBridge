package com.beauver.printbridge.filamentchoice;

import java.util.Objects;

/**
 * Something a filament can be chosen for: one colour of one plate, or a whole item without colours
 * ({@code plateIndex}, {@code type} and {@code color} all {@code null}).
 *
 * @param grams for one copy; {@code null} when unknown
 */
public record FilamentSlot(Integer plateIndex, String type, String color, Double grams) {

    public static FilamentSlot wholeItem(Double grams) {
        return new FilamentSlot(null, null, null, grams);
    }

    public boolean isWholeItem() {
        return plateIndex == null;
    }

    /** Whether this is that colour of that plate (colours ignore case). */
    public boolean matches(Integer otherPlateIndex, String otherType, String otherColor) {
        return Objects.equals(plateIndex, otherPlateIndex)
                && Objects.equals(type, otherType)
                && sameColor(color, otherColor);
    }

    private static boolean sameColor(String first, String second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return first.equalsIgnoreCase(second);
    }
}
