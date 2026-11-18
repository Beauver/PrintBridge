package com.beauver.printbridge.slicing;

import com.beauver.printbridge.modelfile.analysis.ModelSource;
import com.beauver.printbridge.modelfile.threemf.ProjectSettings;
import com.beauver.printbridge.modelfile.ModelFileType;
import com.beauver.printbridge.settings.printer.PrinterSettings;
import com.beauver.printbridge.settings.SlicingSettings;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides on which owned printer and how a file is sliced (#15). Every file is sliced once:
 * <ul>
 *   <li>STL / plain 3MF: the default printer with its default nozzle and profiles.</li>
 *   <li>Project for an owned printer and a nozzle owned on it: that printer and nozzle with the
 *       project's own settings.</li>
 *   <li>Project for an owned printer, but a nozzle that isn't owned on it: that printer's default
 *       nozzle and profiles, keeping the designer's infill, walls and supports.</li>
 *   <li>Project for a printer that isn't owned: the default printer's default nozzle and profiles,
 *       keeping the designer's infill, walls and supports; objects are re-arranged.</li>
 *   <li>Project without saved settings: the default printer and nozzle with the file's settings.</li>
 * </ul>
 */
@ApplicationScoped
public class SlicePlanner {

    /** {@code settings} must have a default printer (slicing can't be switched on without one). */
    public SlicePlan plan(SlicingSettings settings, ModelFileType type, ModelSource source, List<Integer> plates,
                          Optional<ProjectSettings> project) {
        PrinterSettings fallback = settings.defaultPrinter();
        if (type == ModelFileType.STL || source != ModelSource.BAMBU) {
            return new SlicePlan(type.extension(), List.of(1), fallback.model(), fallback.defaultNozzle(), false,
                    fallback.process(), fallback.filament(), 1, Map.of(), true, null, null);
        }
        List<Integer> platesToSlice = plates;
        if (platesToSlice.isEmpty()) {
            platesToSlice = List.of(1);
        }
        if (project.isEmpty()) {
            return new SlicePlan("3mf", platesToSlice, fallback.model(), fallback.defaultNozzle(), false, null, null,
                    1, Map.of(), true, null, null);
        }
        ProjectSettings projectSettings = project.get();
        BigDecimal nozzle = projectSettings.nozzle();
        Optional<PrinterSettings> owned = settings.printer(projectSettings.printerModel());
        if (owned.isPresent()) {
            PrinterSettings printer = owned.get();
            if (printer.owns(nozzle)) {
                return new SlicePlan("3mf", platesToSlice, printer.model(), nozzle, true, null, null, 1, Map.of(),
                        false, null, null);
            }
            if (nozzle == null) {
                return new SlicePlan("3mf", platesToSlice, printer.model(), printer.defaultNozzle(), false, null,
                        null, 1, Map.of(), false, null, null);
            }
            return withDefaults(printer, platesToSlice, projectSettings, false, null);
        }
        return withDefaults(fallback, platesToSlice, projectSettings, true, projectSettings.printerModel());
    }

    /**
     * The plan for the admin's choice (#21) instead of the rules above. The choice was checked
     * when it was queued and again before the job runs.
     * <ul>
     *   <li>STL / plain 3MF / project without saved settings: the chosen printer, nozzle and
     *       profiles, objects re-arranged.</li>
     *   <li>Project with the project's own process (only on its own printer and nozzle): the
     *       project's printer settings, with the chosen filament or the project's.</li>
     *   <li>Project with a chosen process: that process with the designer's infill, walls and
     *       supports on top; re-arranged when it is another printer than the project's.</li>
     * </ul>
     */
    public SlicePlan planCustom(CustomSlice choice, ModelFileType type, ModelSource source, List<Integer> plates,
                                Optional<ProjectSettings> project) {
        if (type == ModelFileType.STL || source != ModelSource.BAMBU) {
            return new SlicePlan(type.extension(), List.of(1), choice.printer(), choice.nozzle(), false,
                    choice.process(), choice.filament(), 1, Map.of(), true, null, null);
        }
        List<Integer> platesToSlice = plates;
        if (platesToSlice.isEmpty()) {
            platesToSlice = List.of(1);
        }
        if (project.isEmpty()) {
            return new SlicePlan("3mf", platesToSlice, choice.printer(), choice.nozzle(), false, choice.process(),
                    choice.filament(), 1, Map.of(), true, null, null);
        }
        ProjectSettings projectSettings = project.get();
        if (choice.process() == null) {
            return new SlicePlan("3mf", platesToSlice, choice.printer(), choice.nozzle(), true, null,
                    choice.filament(), projectSettings.filamentSlots(), Map.of(), false, null, null);
        }
        boolean ownPrinter = projectSettings.printerModel() != null
                && choice.printer().equalsIgnoreCase(projectSettings.printerModel().trim());
        String madeForPrinter = null;
        if (!ownPrinter) {
            madeForPrinter = projectSettings.printerModel();
        }
        BigDecimal madeForNozzle = projectSettings.nozzle();
        if (madeForNozzle != null && madeForNozzle.compareTo(choice.nozzle()) == 0) {
            madeForNozzle = null;
        }
        return new SlicePlan("3mf", platesToSlice, choice.printer(), choice.nozzle(), false, choice.process(),
                choice.filament(), projectSettings.filamentSlots(), projectSettings.keptSettings(), !ownPrinter,
                madeForNozzle, madeForPrinter);
    }

    /** The printer's default nozzle and profiles, with the designer's kept settings on top. */
    private static SlicePlan withDefaults(PrinterSettings printer, List<Integer> plates, ProjectSettings project,
                                          boolean arrange, String madeForPrinter) {
        BigDecimal madeForNozzle = project.nozzle();
        if (madeForNozzle != null && madeForNozzle.compareTo(printer.defaultNozzle()) == 0) {
            madeForNozzle = null;
        }
        return new SlicePlan("3mf", plates, printer.model(), printer.defaultNozzle(), false, printer.process(),
                printer.filament(), project.filamentSlots(), project.keptSettings(), arrange, madeForNozzle,
                madeForPrinter);
    }
}
