package com.beauver.printbridge.modelinfo.dto;

import com.beauver.printbridge.slicing.result.SliceStatus;
import java.math.BigDecimal;

/**
 * Server slicing of a file.
 *
 * @param enabled        server slicing is switched on (the admin may re-slice)
 * @param status         {@code null} when the file was never queued
 * @param printer        printer the numbers are for, once DONE
 * @param nozzle         nozzle (mm) the numbers are for, once DONE
 * @param madeForPrinter the project's printer when it isn't owned and the default printer was used
 * @param madeForNozzle  the project's nozzle when another nozzle was used (not owned, or another printer)
 * @param custom         sliced with the printer, nozzle and profiles the admin chose (#21)
 * @param process        with {@code custom}: the chosen process profile; {@code null} for the project's own
 * @param filament       with {@code custom}: the chosen filament profile; {@code null} for the project's
 * @param error          why the job failed; only for the admin
 */
public record SlicingInfoDto(
        boolean enabled,
        SliceStatus status,
        String printer,
        BigDecimal nozzle,
        String madeForPrinter,
        BigDecimal madeForNozzle,
        boolean custom,
        String process,
        String filament,
        String error) {
}
