package com.beauver.printbridge.settings;

import com.beauver.printbridge.settings.dto.BuildPlateDto;
import com.beauver.printbridge.settings.dto.PrinterDto;
import com.beauver.printbridge.settings.dto.SettingsDto;
import com.beauver.printbridge.settings.dto.SlicingSettingsDto;
import com.beauver.printbridge.settings.dto.SpoolmanSettingsDto;
import com.beauver.printbridge.settings.dto.UploadLimitsDto;
import com.beauver.printbridge.settings.printer.BuildPlate;
import com.beauver.printbridge.settings.printer.Printer;
import com.beauver.printbridge.settings.printer.PrinterSettings;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

/** Settings as the API returns them, and printers as the slicing and model info code use them. */
@ApplicationScoped
public class SettingsMapper {

    /**
     * @param uploads          the upload limits that apply now
     * @param notEnoughMessage the Spoolman warning text that applies now (the default when not set)
     */
    public SettingsDto toDto(AppSettings settings, List<Printer> printers, UploadLimits uploads,
                             String notEnoughMessage) {
        List<PrinterDto> owned = printers.stream().map(this::toDto).toList();
        return new SettingsDto(new SlicingSettingsDto(settings.slicingEnabled, settings.slicingTimeoutMinutes),
                settings.defaultPrinterId, owned,
                new UploadLimitsDto(uploads.maxFiles(), uploads.maxFileSizeMb(), uploads.ceilingMb()),
                new SpoolmanSettingsDto(settings.spoolmanEnabled, settings.spoolmanColorSelection, notEnoughMessage));
    }

    public PrinterDto toDto(Printer printer) {
        return new PrinterDto(printer.id, printer.model,
                new BuildPlateDto(printer.buildPlateX, printer.buildPlateY, printer.buildPlateZ),
                stripAll(printer.ownedNozzles), strip(printer.defaultNozzle), printer.process, printer.filament);
    }

    public PrinterSettings toSettings(Printer printer) {
        return new PrinterSettings(printer.id, printer.model,
                new BuildPlate(printer.buildPlateX, printer.buildPlateY, printer.buildPlateZ),
                stripAll(printer.ownedNozzles), strip(printer.defaultNozzle), printer.process, printer.filament);
    }

    /** The database keeps two decimals (0.40); the API and the planner use 0.4. */
    private static List<BigDecimal> stripAll(List<BigDecimal> nozzles) {
        return nozzles.stream().map(SettingsMapper::strip).toList();
    }

    /** A nozzle size without trailing zeros; {@code null} stays {@code null}. */
    public static BigDecimal strip(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros();
    }
}
