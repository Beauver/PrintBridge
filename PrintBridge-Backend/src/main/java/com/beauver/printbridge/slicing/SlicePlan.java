package com.beauver.printbridge.slicing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * How to slice one file: decided by {@link SlicePlanner}, executed by the slicer service.
 *
 * @param format          {@code stl} or {@code 3mf}
 * @param plates          plate numbers to slice (each one separately)
 * @param printer         printer model as OrcaSlicer names it
 * @param nozzle          nozzle size (mm) to slice for
 * @param projectPrinter  keep the printer settings saved in the project (same printer and nozzle)
 * @param process         process profile to use instead of the project's; {@code null} keeps the project's
 * @param filament        filament profile to use; {@code null} keeps the project's filaments
 * @param filamentSlots   how many filament slots get {@code filament}
 * @param overrides       the designer's settings to keep on top of {@code process}
 * @param arrange         re-arrange objects on the plate (another printer, STL files)
 * @param madeForNozzle   the project's nozzle when it differs from {@code nozzle} because it isn't owned
 */
public record SlicePlan(
        String format,
        List<Integer> plates,
        String printer,
        BigDecimal nozzle,
        boolean projectPrinter,
        String process,
        String filament,
        int filamentSlots,
        Map<String, String> overrides,
        boolean arrange,
        BigDecimal madeForNozzle) {
}
