package com.beauver.printbridge.slicing;

import com.beauver.printbridge.modelinfo.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.analysis.ProjectSettings;
import com.beauver.printbridge.printrequest.ModelFileType;
import com.beauver.printbridge.settings.SlicingSettings;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides how a file is sliced on the admin's printer (#15):
 * <ul>
 *   <li>STL / plain 3MF: the default nozzle with the admin's process and filament profiles.</li>
 *   <li>Project for an owned nozzle: that nozzle, with the project's own process and filaments.
 *       On the same printer model the project's printer settings are kept as well.</li>
 *   <li>Project for a nozzle that isn't owned: the default nozzle with the admin's profiles, but
 *       keeping the designer's infill, walls and supports.</li>
 *   <li>Project without a known nozzle: the default nozzle with the project's settings.</li>
 * </ul>
 * Objects are re-arranged whenever the printer model differs from the project's.
 */
@ApplicationScoped
public class SlicePlanner {

    public SlicePlan plan(SlicingSettings settings, ModelFileType type, ModelSource source, List<Integer> plates,
                          Optional<ProjectSettings> project) {
        if (type == ModelFileType.STL || source != ModelSource.BAMBU) {
            return new SlicePlan(type.extension(), List.of(1), settings.printer(), settings.defaultNozzle(), false,
                    settings.process(), settings.filament(), 1, Map.of(), true, null);
        }
        List<Integer> platesToSlice = plates;
        if (platesToSlice.isEmpty()) {
            platesToSlice = List.of(1);
        }
        boolean samePrinter = project.map(ProjectSettings::printerModel)
                .filter(model -> model.equalsIgnoreCase(settings.printer()))
                .isPresent();
        BigDecimal nozzle = project.map(ProjectSettings::nozzle).orElse(null);
        if (nozzle != null && settings.owns(nozzle)) {
            return new SlicePlan("3mf", platesToSlice, settings.printer(), nozzle, samePrinter, null, null, 1,
                    Map.of(), !samePrinter, null);
        }
        if (nozzle == null) {
            return new SlicePlan("3mf", platesToSlice, settings.printer(), settings.defaultNozzle(), false, null,
                    null, 1, Map.of(), !samePrinter, null);
        }
        ProjectSettings projectSettings = project.get();
        return new SlicePlan("3mf", platesToSlice, settings.printer(), settings.defaultNozzle(), false,
                settings.process(), settings.filament(), projectSettings.filamentSlots(),
                projectSettings.keptSettings(), !samePrinter, nozzle);
    }
}
