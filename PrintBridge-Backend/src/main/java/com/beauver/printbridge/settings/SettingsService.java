package com.beauver.printbridge.settings;

import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.settings.dto.BuildPlateDto;
import com.beauver.printbridge.settings.dto.SettingsDto;
import com.beauver.printbridge.settings.dto.SlicingSettingsDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/** Settings the admin edits in the browser instead of through environment variables. */
@ApplicationScoped
public class SettingsService {

    @Inject
    AppSettingsRepository repository;

    /** Fired after server slicing was switched on or off, inside the saving transaction. */
    @Inject
    Event<SlicingToggled> slicingToggled;

    /** Server slicing was switched on ({@code enabled}) or off. */
    public record SlicingToggled(boolean enabled) {
    }

    public SettingsDto get() {
        return toDto(repository.get());
    }

    @Transactional
    public SettingsDto updateBuildPlate(BuildPlateDto input) {
        AppSettings settings = repository.get();
        settings.buildPlateX = input.x();
        settings.buildPlateY = input.y();
        settings.buildPlateZ = input.z();
        repository.flush();
        return toDto(settings);
    }

    /**
     * Saves the slicing settings. Switching slicing on needs a printer, nozzles and profiles; the
     * default nozzle must be one of the owned ones.
     */
    @Transactional
    public SettingsDto updateSlicing(SlicingSettingsDto input) {
        List<BigDecimal> owned = normalize(input.ownedNozzles());
        BigDecimal defaultNozzle = strip(input.defaultNozzle());
        if (defaultNozzle != null && !owned.isEmpty() && owned.stream().noneMatch(defaultNozzle::equals)) {
            throw new InvalidInputException("The default nozzle must be one of the owned nozzles");
        }
        if (input.enabled() && (isBlank(input.printer()) || owned.isEmpty() || defaultNozzle == null
                || isBlank(input.process()) || isBlank(input.filament()))) {
            throw new InvalidInputException("Choose a printer, nozzles and profiles before switching slicing on");
        }
        AppSettings settings = repository.get();
        boolean wasEnabled = settings.slicingEnabled;
        settings.slicingEnabled = input.enabled();
        settings.slicingTimeoutMinutes = input.timeoutMinutes();
        settings.slicerPrinter = blankToNull(input.printer());
        settings.ownedNozzles = join(owned);
        settings.defaultNozzle = defaultNozzle;
        settings.slicerProcess = blankToNull(input.process());
        settings.slicerFilament = blankToNull(input.filament());
        repository.flush();
        if (wasEnabled != settings.slicingEnabled) {
            slicingToggled.fire(new SlicingToggled(settings.slicingEnabled));
        }
        return toDto(settings);
    }

    /** The build plate, once the admin has set all three sizes. */
    public Optional<BuildPlate> buildPlate() {
        AppSettings settings = repository.get();
        if (settings.buildPlateX == null || settings.buildPlateY == null || settings.buildPlateZ == null) {
            return Optional.empty();
        }
        return Optional.of(new BuildPlate(settings.buildPlateX, settings.buildPlateY, settings.buildPlateZ));
    }

    public SlicingSettings slicing() {
        AppSettings settings = repository.get();
        return new SlicingSettings(settings.slicingEnabled, Duration.ofMinutes(settings.slicingTimeoutMinutes),
                settings.slicerPrinter, split(settings.ownedNozzles), strip(settings.defaultNozzle),
                settings.slicerProcess, settings.slicerFilament);
    }

    private static SettingsDto toDto(AppSettings settings) {
        BuildPlateDto buildPlate = new BuildPlateDto(settings.buildPlateX, settings.buildPlateY, settings.buildPlateZ);
        SlicingSettingsDto slicing = new SlicingSettingsDto(settings.slicingEnabled, settings.slicingTimeoutMinutes,
                settings.slicerPrinter, split(settings.ownedNozzles), strip(settings.defaultNozzle),
                settings.slicerProcess, settings.slicerFilament);
        return new SettingsDto(buildPlate, slicing);
    }

    /** Sorted, without duplicates or trailing zeros (0.40 and 0.4 are the same nozzle). */
    private static List<BigDecimal> normalize(List<BigDecimal> nozzles) {
        if (nozzles == null) {
            return List.of();
        }
        return new ArrayList<>(new TreeSet<>(nozzles.stream().map(SettingsService::strip).toList()));
    }

    private static String join(List<BigDecimal> nozzles) {
        if (nozzles.isEmpty()) {
            return null;
        }
        return String.join(",", nozzles.stream().map(BigDecimal::toPlainString).toList());
    }

    private static List<BigDecimal> split(String nozzles) {
        if (nozzles == null || nozzles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(nozzles.split(",")).map(BigDecimal::new).map(SettingsService::strip).toList();
    }

    private static BigDecimal strip(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
