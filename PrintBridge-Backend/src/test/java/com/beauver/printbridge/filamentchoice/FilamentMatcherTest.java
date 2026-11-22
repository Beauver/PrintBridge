package com.beauver.printbridge.filamentchoice;

import com.beauver.printbridge.spoolman.SpoolmanFilament;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FilamentMatcherTest {

    private static SpoolmanFilament filament(long id, String material, String color) {
        return new SpoolmanFilament(id, null, null, material, color, List.of(), 0.0, null, null, null);
    }

    private static Optional<Long> match(String type, String color, double tolerance, SpoolmanFilament... filaments) {
        return FilamentMatcher.closest(new FilamentSlot(1, type, color, null), List.of(filaments), tolerance)
                .map(SpoolmanFilament::id);
    }

    @Test
    void picksTheClosestOfTheSameMaterialWithinTheTolerance() {
        SpoolmanFilament offWhite = filament(1, "PLA", "#FAFAFA");
        SpoolmanFilament grey = filament(2, "PLA", "#C0C0C0");
        SpoolmanFilament whitePetg = filament(3, "PETG", "#FFFFFF");

        assertEquals(Optional.of(1L), match("PLA", "#FFFFFF", 20, grey, offWhite, whitePetg));
        assertEquals(Optional.of(1L), match("pla ", "#FFFFFF", 20, grey, offWhite, whitePetg));
        assertEquals(Optional.of(3L), match("PETG", "#FFFFFF", 20, grey, offWhite, whitePetg));
        // Nothing of that material at all, or not close enough.
        assertEquals(Optional.empty(), match("TPU", "#FFFFFF", 100, grey, offWhite, whitePetg));
        assertEquals(Optional.empty(), match("PLA", "#FF0000", 20, grey, offWhite));
        // The tolerance decides: grey is about ΔE 20 from white.
        assertEquals(Optional.empty(), match("PLA", "#FFFFFF", 5, grey));
        assertEquals(Optional.of(2L), match("PLA", "#FFFFFF", 35, grey));
    }

    @Test
    void anyMaterialWhenTheFileDoesNotSayAndTiesGoToTheLowestId() {
        assertEquals(Optional.of(3L), match(null, "#FFFFFF", 20, filament(5, "PLA", "#C0C0C0"),
                filament(3, "PETG", "#FFFFFF"), filament(4, "PLA", "#FFFFFF")));
        assertEquals(Optional.of(3L), match(" ", "#FFFFFF", 20, filament(3, "PETG", "#FFFFFF")));
    }

    @Test
    void skipsMultiColourFilamentsAndUnknownColours() {
        SpoolmanFilament dual = new SpoolmanFilament(1, null, null, "PLA", null, List.of("#FFFFFF", "#FFFFFE"), 0.0,
                null, null, null);
        SpoolmanFilament noColour = filament(2, "PLA", null);
        SpoolmanFilament noMaterial = filament(3, null, "#FFFFFF");
        assertEquals(Optional.empty(), match("PLA", "#FFFFFF", 100, dual, noColour, noMaterial));
        assertEquals(Optional.empty(), match("PLA", null, 100, filament(4, "PLA", "#FFFFFF")));
        // Anything but #RRGGBB is skipped instead of failing the matching.
        assertEquals(Optional.empty(), match("PLA", "white", 100, filament(4, "PLA", "#FFFFFF")));
        assertEquals(Optional.of(4L), match("PLA", "#FFFFFF", 100, filament(5, "PLA", "#FFF"),
                filament(4, "PLA", "#FFFFFF")));
    }
}
