package com.beauver.printbridge.slicing.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the "Re-slice with…" dialog of one file offers (#21). The process and filament lists come
 * from {@code GET /slicer/profiles} per printer and nozzle.
 *
 * @param printers       owned printers that are set up for slicing
 * @param project        a Bambu Studio / OrcaSlicer project with saved settings: offers the
 *                       project's filaments, and on its own printer and nozzle its own process
 * @param projectPrinter the printer the project was made for; {@code null} for other files
 * @param projectNozzle  the nozzle the project was made for; {@code null} for other files
 * @param start          the values the dialog starts with
 */
public record ResliceOptionsDto(
        List<ReslicePrinterDto> printers,
        boolean project,
        String projectPrinter,
        BigDecimal projectNozzle,
        ResliceChoiceDto start) {
}
