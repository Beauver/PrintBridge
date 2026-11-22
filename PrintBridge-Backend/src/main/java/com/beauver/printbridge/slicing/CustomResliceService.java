package com.beauver.printbridge.slicing;

import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.modelfile.threemf.ProjectSettings;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.settings.SlicingSettings;
import com.beauver.printbridge.settings.printer.PrinterSettings;
import com.beauver.printbridge.slicing.dto.ReslicePrinterDto;
import com.beauver.printbridge.slicing.dto.ResliceChoiceDto;
import com.beauver.printbridge.slicing.dto.ResliceOptionsDto;
import com.beauver.printbridge.slicing.dto.SlicerProfilesDto;
import com.beauver.printbridge.slicing.result.SliceResult;
import com.beauver.printbridge.slicing.result.SliceStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Re-slicing one file with the printer, nozzle and profiles the admin chose (#21): what the
 * dialog offers, and checking a choice before it is queued.
 */
@ApplicationScoped
public class CustomResliceService {

    @Inject
    SettingsService settings;

    @Inject
    SlicingService slicing;

    @Inject
    SlicerService slicer;

    /** The printers, whether the file is a project, and where the dialog starts. */
    public ResliceOptionsDto options(long fileId) {
        SlicingSettings slicingSettings = settings.getSlicing();
        List<PrinterSettings> printers = slicingSettings.printers().stream()
                .filter(PrinterSettings::readyForSlicing)
                .toList();
        Optional<ProjectSettings> project = slicing.projectSettings(fileId);
        ResliceChoiceDto start = start(printers, slicingSettings.defaultPrinter(), project,
                slicing.find(fileId).orElse(null));
        String projectPrinter = null;
        BigDecimal projectNozzle = null;
        if (project.isPresent()) {
            projectPrinter = project.get().printerModel();
            projectNozzle = project.get().nozzle();
        }
        return new ResliceOptionsDto(printers.stream().map(CustomResliceService::toDto).toList(), project.isPresent(),
                projectPrinter, projectNozzle, start);
    }

    /**
     * Checks the admin's choice and queues the file with it. 409 while slicing is off or the
     * file is queued or running; 400 for a printer or nozzle that isn't owned, a profile the
     * slicer doesn't have for that printer and nozzle, or the project's own settings where they
     * aren't possible; 503 when the slicer can't be reached to check the profiles.
     *
     * @param process  {@code null} or blank: the project's own settings
     * @param filament {@code null} or blank: the project's filaments
     */
    @Transactional
    public void reslice(long fileId, long printerId, BigDecimal nozzle, String process, String filament) {
        slicing.checkMayReslice(fileId);
        PrinterSettings printer = settings.getSlicing().printers().stream()
                .filter(candidate -> candidate.id() == printerId && candidate.readyForSlicing())
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("Choose one of your printers"));
        if (!printer.owns(nozzle)) {
            throw new InvalidInputException("You don't own a " + mm(nozzle) + " nozzle for " + printer.model());
        }
        Optional<ProjectSettings> project = slicing.projectSettings(fileId);
        String chosenProcess = blankToNull(process);
        String chosenFilament = blankToNull(filament);
        if (chosenProcess == null && !ownSettingsPossible(project, printer.model(), nozzle)) {
            if (project.isPresent()) {
                throw new InvalidInputException("The project's own settings only work on the printer and nozzle "
                        + "it was made for; choose a process profile");
            }
            throw new InvalidInputException("Choose a process profile");
        }
        if (chosenFilament == null && project.isEmpty()) {
            throw new InvalidInputException("Choose a filament profile");
        }
        if (chosenProcess != null || chosenFilament != null) {
            SlicerProfilesDto profiles = slicer.profiles(printer.model(), nozzle);
            if (chosenProcess != null && !profiles.processes().contains(chosenProcess)) {
                throw new InvalidInputException("The process profile " + chosenProcess + " doesn't exist for "
                        + printer.model() + " with a " + mm(nozzle) + " nozzle");
            }
            if (chosenFilament != null && !profiles.filaments().contains(chosenFilament)) {
                throw new InvalidInputException("The filament profile " + chosenFilament + " doesn't exist for "
                        + printer.model() + " with a " + mm(nozzle) + " nozzle");
            }
        }
        slicing.resliceWith(fileId, new CustomSlice(printer.model(), nozzle, chosenProcess, chosenFilament));
    }

    /** The project's own process only on the printer and nozzle it was made for. */
    static boolean ownSettingsPossible(Optional<ProjectSettings> project, String printer, BigDecimal nozzle) {
        if (project.isEmpty()) {
            return false;
        }
        ProjectSettings settings = project.get();
        return settings.printerModel() != null && settings.printerModel().trim().equalsIgnoreCase(printer)
                && settings.nozzle() != null && settings.nozzle().compareTo(nozzle) == 0;
    }

    /**
     * The last custom choice while its printer and nozzle are still owned; else the printer and
     * nozzle the file was last sliced on, else the default printer and its default nozzle. The
     * profiles are the printer's own, or the project's where that's possible.
     */
    static ResliceChoiceDto start(List<PrinterSettings> printers, PrinterSettings defaultPrinter,
                                  Optional<ProjectSettings> project, SliceResult result) {
        if (result != null && result.custom) {
            Optional<PrinterSettings> chosen = byModel(printers, result.chosenPrinter);
            if (chosen.isPresent() && chosen.get().owns(result.chosenNozzle)) {
                return new ResliceChoiceDto(chosen.get().id(), result.chosenNozzle, result.chosenProcess,
                        result.chosenFilament, true);
            }
        }
        Optional<PrinterSettings> printer = Optional.empty();
        if (result != null && result.status == SliceStatus.DONE) {
            printer = byModel(printers, result.printer);
        }
        if (printer.isEmpty() && defaultPrinter != null) {
            printer = byModel(printers, defaultPrinter.model());
        }
        if (printer.isEmpty() && !printers.isEmpty()) {
            printer = Optional.of(printers.getFirst());
        }
        if (printer.isEmpty()) {
            return new ResliceChoiceDto(null, null, null, null, false);
        }
        PrinterSettings startPrinter = printer.get();
        BigDecimal nozzle = startPrinter.defaultNozzle();
        if (result != null && result.status == SliceStatus.DONE && startPrinter.isModel(result.printer)
                && startPrinter.owns(result.nozzle)) {
            nozzle = result.nozzle;
        }
        if (project.isEmpty()) {
            return new ResliceChoiceDto(startPrinter.id(), nozzle, startPrinter.process(), startPrinter.filament(),
                    false);
        }
        if (ownSettingsPossible(project, startPrinter.model(), nozzle)) {
            return new ResliceChoiceDto(startPrinter.id(), nozzle, null, null, false);
        }
        return new ResliceChoiceDto(startPrinter.id(), nozzle, startPrinter.process(), null, false);
    }

    private static Optional<PrinterSettings> byModel(List<PrinterSettings> printers, String model) {
        return printers.stream().filter(printer -> printer.isModel(model)).findFirst();
    }

    private static ReslicePrinterDto toDto(PrinterSettings printer) {
        return new ReslicePrinterDto(printer.id(), printer.model(), printer.ownedNozzles(), printer.defaultNozzle(),
                printer.process(), printer.filament());
    }

    private static String mm(BigDecimal nozzle) {
        if (nozzle == null) {
            return "?";
        }
        return nozzle.stripTrailingZeros().toPlainString() + " mm";
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
