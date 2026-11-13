package com.beauver.printbridge.slicing;

import com.beauver.printbridge.modelinfo.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.analysis.ProjectSettings;
import com.beauver.printbridge.printrequest.ModelFileType;
import com.beauver.printbridge.settings.PrinterSettings;
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
