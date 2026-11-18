package com.beauver.printbridge.slicing;

import java.math.BigDecimal;

/**
 * How the admin chose to slice one file (#21), instead of the automatic rules.
 *
 * @param printer  owned printer model, as OrcaSlicer names it
 * @param nozzle   nozzle size (mm) owned on that printer
 * @param process  process profile; {@code null} keeps the project's own settings (a project on its
 *                 own printer and nozzle only)
 * @param filament filament profile for every slot; {@code null} keeps the project's filaments
 *                 (projects only)
 */
public record CustomSlice(String printer, BigDecimal nozzle, String process, String filament) {
}
