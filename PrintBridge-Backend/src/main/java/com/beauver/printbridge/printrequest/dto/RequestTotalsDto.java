package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.modelinfo.dto.WeightKind;

/**
 * The whole request: every file times its quantity (#23).
 *
 * @param files        number of files
 * @param copies       sum of the quantities
 * @param grams        filament over the files whose weight is known; {@code null} when none is
 * @param weight       how much of {@code grams} is exact
 * @param printSeconds     print time over the files whose time is known; {@code null} when none is
 * @param printTimePartial some files have no print time yet (not sliced), so {@code printSeconds} is too low
 * @param fits         {@code false} when any file doesn't fit; {@code null} when no file has a fit check
 */
public record RequestTotalsDto(
        int files,
        int copies,
        Double grams,
        WeightKind weight,
        Long printSeconds,
        boolean printTimePartial,
        Boolean fits) {
}
