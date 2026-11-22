package com.beauver.printbridge.filamentchoice;

import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceDto;
import com.beauver.printbridge.spoolman.SpoolmanFilament;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.List;

/** Filament choices as the API returns them, and Spoolman filaments copied onto a choice. */
@ApplicationScoped
public class FilamentChoiceMapper {

    /** Copies what the request page shows of the filament onto the choice (cut to the column sizes). */
    public void copy(SpoolmanFilament filament, FilamentChoice choice) {
        choice.spoolmanFilamentId = filament.id();
        choice.filamentName = cut(filament.name(), 200);
        choice.filamentVendor = cut(filament.vendor(), 200);
        choice.filamentMaterial = cut(filament.material(), 100);
        choice.filamentColor = filament.color();
        choice.filamentColors = null;
        if (!filament.colors().isEmpty()) {
            // At most 25 colours of 8 characters fit; Spoolman filaments have a handful.
            choice.filamentColors = String.join(",", filament.colors().stream().limit(25).toList());
        }
    }

    /**
     * @param inSpoolman whether Spoolman still has it; {@code null} when unknown
     * @param current    the filament in Spoolman now; {@code null} when unknown or deleted
     */
    public FilamentChoiceDto toDto(FilamentChoice choice, boolean orphaned, Boolean inSpoolman,
                                   SpoolmanFilament current, Double gramsNeeded) {
        Double gramsLeft = null;
        boolean notEnough = false;
        if (current != null) {
            gramsLeft = current.gramsLeft();
            notEnough = !orphaned && current.notEnoughFor(gramsNeeded);
        }
        return new FilamentChoiceDto(choice.id, choice.plateIndex == null, choice.plateIndex, choice.originalType,
                choice.originalColor,
                choice.spoolmanFilamentId, choice.filamentName, choice.filamentVendor, choice.filamentMaterial,
                choice.filamentColor, colors(choice.filamentColors), choice.chosenAt, orphaned, inSpoolman, gramsLeft,
                gramsNeeded, notEnough, choice.source);
    }

    private static List<String> colors(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.asList(joined.split(","));
    }

    private static String cut(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
