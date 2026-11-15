package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.slicing.result.SliceStatus;

import java.math.BigDecimal;

/**
 * Server slicing of a model file.
 *
 * @param enabled whether server slicing is switched on
 * @param status  {@code null} when the file was never queued
 * @param error   why the job failed; only filled in for the admin
 */
public record SlicingDetails(
        boolean enabled,
        SliceStatus status,
        String printer,
        BigDecimal nozzle,
        String madeForPrinter,
        BigDecimal madeForNozzle,
        String error) {
}
