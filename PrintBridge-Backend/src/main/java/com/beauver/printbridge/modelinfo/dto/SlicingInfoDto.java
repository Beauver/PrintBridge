package com.beauver.printbridge.modelinfo.dto;

import com.beauver.printbridge.slicing.SliceStatus;

import java.math.BigDecimal;

/**
 * Server slicing of a file.
 *
 * @param enabled       server slicing is switched on (the admin may re-slice)
 * @param status        {@code null} when the file was never queued
 * @param nozzle        nozzle (mm) the numbers are for, once DONE
 * @param madeForNozzle the project's nozzle when it isn't owned and the default nozzle was used
 * @param error         why the job failed; only for the admin
 */
public record SlicingInfoDto(
        boolean enabled,
        SliceStatus status,
        BigDecimal nozzle,
        BigDecimal madeForNozzle,
        String error) {
}
