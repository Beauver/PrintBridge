package com.beauver.printbridge.settings;

import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.settings.dto.BuildPlateDto;
import com.beauver.printbridge.settings.dto.PrinterDto;
import com.beauver.printbridge.settings.dto.PrinterInputDto;
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

/**
 * Settings the admin edits in the browser instead of through environment variables: server
 * slicing and the owned printers (build plate and slicing setup each).
 */
@ApplicationScoped
public class SettingsService {

    @Inject
    AppSettingsRepository repository;

    @Inject
    PrinterRepository printers;

    /** Fired after server slicing was switched on or off, inside the saving transaction. */
    @Inject
    Event<SlicingToggled> slicingToggled;

    /** Server slicing was switched on ({@code enabled}) or off. */
    public record SlicingToggled(boolean enabled) {
    }

    @Transactional
    public SettingsDto get() {
        return toDto(repository.get());
    }

    /** Switching slicing on needs at least one printer, and every printer ready for slicing. */
    @Transactional
    public SettingsDto updateSlicing(SlicingSettingsDto input) {
        if (input.enabled()) {
            List<PrinterSettings> owned = printerSettings();
            if (owned.isEmpty()) {
                throw new InvalidInputException("Add a printer before switching slicing on");
            }
            owned.stream().filter(printer -> !printer.readyForSlicing()).findFirst().ifPresent(printer -> {
                throw new InvalidInputException(
                        "Choose nozzles and profiles for " + printer.model() + " before switching slicing on");
            });
        }
        AppSettings settings = repository.get();
        boolean wasEnabled = settings.slicingEnabled;
        settings.slicingEnabled = input.enabled();
        settings.slicingTimeoutMinutes = input.timeoutMinutes();
        repository.flush();
        if (wasEnabled != settings.slicingEnabled) {
            slicingToggled.fire(new SlicingToggled(settings.slicingEnabled));
        }
        return toDto(settings);
    }

    /** Adds a printer; the first one becomes the default printer. */
    @Transactional
    public SettingsDto addPrinter(PrinterInputDto input) {
        AppSettings settings = repository.get();
        Printer printer = new Printer();
        apply(printer, input, settings);
        printers.persist(printer);
        if (settings.defaultPrinterId == null) {
            settings.defaultPrinterId = printer.id;
        }
        repository.flush();
        return toDto(settings);
    }

    @Transactional
    public SettingsDto updatePrinter(long id, PrinterInputDto input) {
        AppSettings settings = repository.get();
        apply(findPrinter(id), input, settings);
        printers.flush();
        return toDto(settings);
    }

    /**
     * Removes a printer. Removing the default printer makes the oldest remaining one the default;
     * the last printer can't go while slicing is on. Slice results keep the printer's name.
     */
    @Transactional
    public SettingsDto deletePrinter(long id) {
        AppSettings settings = repository.get();
        Printer printer = findPrinter(id);
        if (settings.slicingEnabled && printers.count() == 1) {
            throw new ConflictException("Switch server slicing off before removing your last printer");
        }
        printers.delete(printer);
        printers.flush();
        if (Long.valueOf(id).equals(settings.defaultPrinterId)) {
            settings.defaultPrinterId = printers.listInOrder().stream().findFirst().map(other -> other.id).orElse(null);
        }
        repository.flush();
        return toDto(settings);
    }

    @Transactional
    public SettingsDto makeDefault(long id) {
        AppSettings settings = repository.get();
        settings.defaultPrinterId = findPrinter(id).id;
        repository.flush();
        return toDto(settings);
    }

    /** The slicing settings with all owned printers. */
    @Transactional
    public SlicingSettings slicing() {
        AppSettings settings = repository.get();
        List<PrinterSettings> owned = printerSettings();
        PrinterSettings defaultPrinter = owned.stream()
                .filter(printer -> settings.defaultPrinterId != null && printer.id() == settings.defaultPrinterId)
                .findFirst()
                .orElse(null);
        return new SlicingSettings(settings.slicingEnabled, Duration.ofMinutes(settings.slicingTimeoutMinutes), owned,
                defaultPrinter);
    }

    private void apply(Printer printer, PrinterInputDto input, AppSettings settings) {
        String model = input.model().trim();
        Optional<Printer> sameModel = printers.findByModel(model);
        if (sameModel.isPresent() && !sameModel.get().id.equals(printer.id)) {
            throw new ConflictException("You already have a printer called " + sameModel.get().model);
        }
        List<BigDecimal> owned = normalize(input.ownedNozzles());
        BigDecimal defaultNozzle = strip(input.defaultNozzle());
        if (defaultNozzle != null && !owned.isEmpty() && owned.stream().noneMatch(defaultNozzle::equals)) {
            throw new InvalidInputException("The default nozzle must be one of the owned nozzles");
        }
        PrinterSettings result = new PrinterSettings(0, model, null, owned, defaultNozzle,
                blankToNull(input.process()), blankToNull(input.filament()));
        if (settings.slicingEnabled && !result.readyForSlicing()) {
            throw new InvalidInputException("Choose nozzles and profiles for this printer while server slicing is on");
        }
        BuildPlateDto buildPlate = input.buildPlate();
        printer.model = model;
        printer.buildPlateX = buildPlate.x();
        printer.buildPlateY = buildPlate.y();
        printer.buildPlateZ = buildPlate.z();
        printer.ownedNozzles = join(owned);
        printer.defaultNozzle = defaultNozzle;
        printer.process = result.process();
        printer.filament = result.filament();
    }

    private Printer findPrinter(long id) {
        return printers.findByIdOptional(id).orElseThrow(() -> new ResourceNotFoundException("Printer not found"));
    }

    private List<PrinterSettings> printerSettings() {
        return printers.listInOrder().stream().map(SettingsService::toSettings).toList();
    }

    private SettingsDto toDto(AppSettings settings) {
        List<PrinterDto> owned = printers.listInOrder().stream().map(SettingsService::toDto).toList();
        return new SettingsDto(new SlicingSettingsDto(settings.slicingEnabled, settings.slicingTimeoutMinutes),
                settings.defaultPrinterId, owned);
    }

    private static PrinterSettings toSettings(Printer printer) {
        return new PrinterSettings(printer.id, printer.model,
                new BuildPlate(printer.buildPlateX, printer.buildPlateY, printer.buildPlateZ),
                split(printer.ownedNozzles), strip(printer.defaultNozzle), printer.process, printer.filament);
    }

    private static PrinterDto toDto(Printer printer) {
        return new PrinterDto(printer.id, printer.model,
                new BuildPlateDto(printer.buildPlateX, printer.buildPlateY, printer.buildPlateZ),
                split(printer.ownedNozzles), strip(printer.defaultNozzle), printer.process, printer.filament);
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

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
