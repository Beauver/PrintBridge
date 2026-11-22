package com.beauver.printbridge.filamentchoice;

import com.beauver.printbridge.spoolman.SpoolmanFilament;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the Spoolman filament closest to a colour of a file (#76): same material (any when the
 * file doesn't name one), a single known colour, and a colour difference within the tolerance.
 * Stock doesn't count.
 */
public final class FilamentMatcher {

    private FilamentMatcher() {
    }

    /** Empty when the colour isn't known (or not {@code #RRGGBB}) or no filament is close enough. */
    public static Optional<SpoolmanFilament> closest(FilamentSlot slot, List<SpoolmanFilament> filaments,
                                                     double tolerance) {
        if (!ColorDifference.isColor(slot.color())) {
            return Optional.empty();
        }
        return filaments.stream()
                .filter(filament -> ColorDifference.isColor(filament.color()) && filament.colors().isEmpty())
                .filter(filament -> sameMaterial(slot.type(), filament.material()))
                .map(filament -> new Candidate(filament, ColorDifference.between(slot.color(), filament.color())))
                .filter(candidate -> candidate.difference() <= tolerance)
                .min(Comparator.comparingDouble(Candidate::difference).thenComparingLong(candidate -> {
                    return candidate.filament().id();
                }))
                .map(Candidate::filament);
    }

    private record Candidate(SpoolmanFilament filament, double difference) {
    }

    private static boolean sameMaterial(String fileType, String material) {
        if (fileType == null || fileType.isBlank()) {
            return true;
        }
        return material != null && fileType.trim().equalsIgnoreCase(material.trim());
    }
}
