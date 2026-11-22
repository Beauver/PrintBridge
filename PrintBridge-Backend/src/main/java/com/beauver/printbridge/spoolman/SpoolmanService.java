package com.beauver.printbridge.spoolman;

import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.spoolman.dto.SpoolmanFilamentDto;
import com.beauver.printbridge.spoolman.dto.SpoolmanStatusDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;

/** Spoolman for the settings page and the filament dropdown (#11). */
@ApplicationScoped
public class SpoolmanService {

    static final String SELECTION_OFF = "Choosing filaments from Spoolman is switched off";

    @Inject
    SpoolmanClient client;

    @Inject
    SpoolmanCatalog catalog;

    @Inject
    SettingsService settings;

    /** Whether Spoolman is set up and answers; never throws, the reason is in the status. */
    public SpoolmanStatusDto status() {
        if (!client.isConfigured()) {
            return new SpoolmanStatusDto(false, false, null, null, SpoolmanClient.NOT_SET_UP);
        }
        try {
            return new SpoolmanStatusDto(true, true, client.address(), client.version(), null);
        } catch (SpoolmanException e) {
            return new SpoolmanStatusDto(true, false, client.address(), null, e.getMessage());
        }
    }

    /**
     * Every filament in Spoolman, also used-up ones (the requester gets a warning instead), sorted
     * by material, vendor and name. Only while colour selection is on.
     *
     * @throws ConflictException  when colour selection is off
     * @throws SpoolmanException when Spoolman can't be read
     */
    public List<SpoolmanFilamentDto> filaments() {
        if (!settings.getSpoolman().colorSelection()) {
            throw new ConflictException(SELECTION_OFF);
        }
        return catalog.filaments().stream()
                .sorted(Comparator.comparing(SpoolmanFilament::material, SpoolmanService::compareText)
                        .thenComparing(SpoolmanFilament::vendor, SpoolmanService::compareText)
                        .thenComparing(SpoolmanFilament::name, SpoolmanService::compareText)
                        .thenComparingLong(SpoolmanFilament::id))
                .map(SpoolmanService::toDto)
                .toList();
    }

    static SpoolmanFilamentDto toDto(SpoolmanFilament filament) {
        return new SpoolmanFilamentDto(filament.id(), filament.name(), filament.vendor(), filament.material(),
                filament.color(), filament.colors(), filament.gramsLeft());
    }

    /** Alphabetical ignoring case, missing values last. */
    private static int compareText(String firstText, String secondText) {
        if (firstText == null || secondText == null) {
            return Boolean.compare(firstText == null, secondText == null);
        }
        return firstText.compareToIgnoreCase(secondText);
    }
}
