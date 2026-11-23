package com.beauver.printbridge.settings;

import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.settings.dto.BuildPlateDto;
import com.beauver.printbridge.settings.dto.PricingSettingsDto;
import com.beauver.printbridge.settings.dto.PrinterInputDto;
import com.beauver.printbridge.settings.dto.SettingsDto;
import com.beauver.printbridge.settings.dto.SlicingSettingsDto;
import com.beauver.printbridge.settings.dto.SpoolmanSettingsDto;
import com.beauver.printbridge.settings.dto.UploadSettingsDto;
import com.beauver.printbridge.settings.printer.Printer;
import com.beauver.printbridge.settings.printer.PrinterRepository;
import com.beauver.printbridge.settings.printer.PrinterSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Settings the admin edits in the browser instead of through environment variables: server
 * slicing, the owned printers (build plate and slicing setup each), the upload limits and the
 * Spoolman switches.
 */
@ApplicationScoped
public class SettingsService {

    @Inject
    AppSettingsRepository repository;

    @Inject
    PrinterRepository printers;

    @Inject
    UploadConfig uploadConfig;

    @Inject
    SettingsMapper mapper;

    @Inject
    SpoolmanConnection spoolman;

    /** Fired after the Spoolman integration was switched on or off, inside the saving transaction. */
    @Inject
    Event<SpoolmanToggled> spoolmanToggled;

    /** Fired after server slicing was switched on or off, inside the saving transaction. */
    @Inject
    Event<SlicingToggled> slicingToggled;

    /** Server slicing was switched on ({@code enabled}) or off. */
    public record SlicingToggled(boolean enabled) {
    }

    /** The Spoolman integration was switched on ({@code enabled}) or off. */
    public record SpoolmanToggled(boolean enabled) {
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

    /**
     * Sets the upload limits (#23). The size per file can't go above the server's ceiling
     * ({@code UPLOAD_MAX_SIZE_MB}): the HTTP layer refuses bigger bodies before the app sees them.
     */
    @Transactional
    public SettingsDto updateUploads(UploadSettingsDto input) {
        int ceiling = uploadConfig.maxSizeMb();
        if (input.maxFileSizeMb() > ceiling) {
            throw new InvalidInputException("Max size per file can be at most " + ceiling
                    + " MB; raise UPLOAD_MAX_SIZE_MB on the server to allow more");
        }
        AppSettings settings = repository.get();
        settings.uploadMaxFiles = input.maxFiles();
        settings.uploadMaxFileSizeMb = input.maxFileSizeMb();
        repository.flush();
        return toDto(settings);
    }

    /**
     * Switches the Spoolman integration, colour selection (#11) and Spoolman prices for the estimate
     * (#72). All need {@code SPOOLMAN_URL}; colour selection and prices need the integration.
     */
    @Transactional
    public SettingsDto updateSpoolman(SpoolmanSettingsDto input) {
        boolean enabled = input.enabled();
        boolean colorSelection = input.colorSelection();
        boolean prices = input.prices();
        boolean autoMatch = input.autoMatch();
        if ((enabled || colorSelection || prices || autoMatch) && !spoolman.isConfigured()) {
            throw new InvalidInputException("Set SPOOLMAN_URL on the server before switching Spoolman on");
        }
        if (colorSelection && !enabled) {
            throw new InvalidInputException("Switch the Spoolman integration on before colour selection");
        }
        if (prices && !enabled) {
            throw new InvalidInputException("Switch the Spoolman integration on before using its prices");
        }
        if (autoMatch && !colorSelection) {
            throw new InvalidInputException("Switch colour selection on before automatic matching");
        }
        AppSettings settings = repository.get();
        boolean wasEnabled = settings.spoolmanEnabled;
        settings.spoolmanEnabled = enabled;
        settings.spoolmanColorSelection = colorSelection;
        settings.spoolmanPrices = prices;
        settings.spoolmanAutoMatch = autoMatch;
        settings.spoolmanMatchTolerance = input.matchTolerance();
        settings.spoolmanNotEnoughMessage = customMessage(input.notEnoughMessage(),
                SpoolmanSettings.DEFAULT_NOT_ENOUGH_MESSAGE);
        settings.spoolmanNoMatchMessage = customMessage(input.noMatchMessage(),
                SpoolmanSettings.DEFAULT_NO_MATCH_MESSAGE);
        repository.flush();
        if (wasEnabled != enabled) {
            spoolmanToggled.fire(new SpoolmanToggled(enabled));
        }
        return toDto(settings);
    }

    /**
     * The Spoolman settings that apply now: the switches are off while {@code SPOOLMAN_URL} isn't
     * set, and the warning text is the default unless the admin set one.
     */
    @Transactional
    public SpoolmanSettings getSpoolman() {
        AppSettings settings = repository.get();
        String notEnough = message(settings.spoolmanNotEnoughMessage, SpoolmanSettings.DEFAULT_NOT_ENOUGH_MESSAGE);
        String noMatch = message(settings.spoolmanNoMatchMessage, SpoolmanSettings.DEFAULT_NO_MATCH_MESSAGE);
        if (!spoolman.isConfigured()) {
            return new SpoolmanSettings(false, false, false, false, settings.spoolmanMatchTolerance, notEnough,
                    noMatch);
        }
        boolean colorSelection = settings.spoolmanEnabled && settings.spoolmanColorSelection;
        return new SpoolmanSettings(settings.spoolmanEnabled, colorSelection,
                settings.spoolmanEnabled && settings.spoolmanPrices, colorSelection && settings.spoolmanAutoMatch,
                settings.spoolmanMatchTolerance, notEnough, noMatch);
    }

    /**
     * Sets the currency (#75), and the markup and the default price per 100 g of the estimate (#72).
     * Whether Spoolman's prices are used is set with the Spoolman switches. Changing the currency
     * relabels every amount; nothing is converted.
     */
    @Transactional
    public SettingsDto updatePricing(PricingSettingsDto input) {
        AppSettings settings = repository.get();
        settings.currency = input.currency().toUpperCase(Locale.ROOT);
        settings.pricingMarkup = input.markup();
        settings.pricePer100Grams = input.pricePer100Grams();
        repository.flush();
        return toDto(settings);
    }

    /** The price estimate settings that apply now: Spoolman prices only while the integration is on. */
    @Transactional
    public PricingSettings getPricing() {
        AppSettings settings = repository.get();
        return new PricingSettings(getSpoolman().prices(), settings.pricingMarkup, settings.pricePer100Grams);
    }

    /** ISO 4217 code of every price (#75), e.g. EUR. */
    @Transactional
    public String getCurrency() {
        return repository.get().currency;
    }

    /** The upload limits that apply now. */
    @Transactional
    public UploadLimits getUploads() {
        return getUploads(repository.get());
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
    public SlicingSettings getSlicing() {
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
        BigDecimal defaultNozzle = SettingsMapper.strip(input.defaultNozzle());
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
        printer.ownedNozzles = owned;
        printer.defaultNozzle = defaultNozzle;
        printer.process = result.process();
        printer.filament = result.filament();
    }

    private Printer findPrinter(long id) {
        return printers.findByIdOptional(id).orElseThrow(() -> new ResourceNotFoundException("Printer not found"));
    }

    private SettingsDto toDto(AppSettings settings) {
        return mapper.toDto(settings, printers.listInOrder(), getUploads(settings), getSpoolman());
    }

    private List<PrinterSettings> printerSettings() {
        return printers.listInOrder().stream().map(mapper::toSettings).toList();
    }

    /**
     * Unset limits take their default. A saved size above the ceiling (the ceiling was lowered
     * in the environment later) is capped, since bigger uploads can't get through anyway.
     */
    private UploadLimits getUploads(AppSettings settings) {
        int ceiling = uploadConfig.maxSizeMb();
        int maxFiles = UploadLimits.DEFAULT_MAX_FILES;
        if (settings.uploadMaxFiles != null) {
            maxFiles = settings.uploadMaxFiles;
        }
        int maxFileSize = ceiling;
        if (settings.uploadMaxFileSizeMb != null) {
            maxFileSize = Math.min(settings.uploadMaxFileSizeMb, ceiling);
        }
        return new UploadLimits(maxFiles, maxFileSize, ceiling);
    }

    /** The admin's text, else the default. */
    private static String message(String saved, String defaultText) {
        if (saved == null) {
            return defaultText;
        }
        return saved;
    }

    /** What to store for a warning text: {@code null} (the default) when empty or the default itself. */
    private static String customMessage(String message, String defaultText) {
        String trimmed = blankToNull(message);
        if (trimmed == null || trimmed.equals(defaultText)) {
            return null;
        }
        return trimmed;
    }

    /** Sorted, without duplicates or trailing zeros (0.40 and 0.4 are the same nozzle). */
    private static List<BigDecimal> normalize(List<BigDecimal> nozzles) {
        if (nozzles == null) {
            return List.of();
        }
        return new ArrayList<>(new TreeSet<>(nozzles.stream().map(SettingsMapper::strip).toList()));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
