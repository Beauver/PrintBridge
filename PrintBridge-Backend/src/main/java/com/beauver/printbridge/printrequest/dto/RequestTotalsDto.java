package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.modelinfo.WeightKind;

/**
 * The whole request: every file and link times its quantity (#23, #59). Declined items (#61)
 * only count in {@code files}, {@code links} and {@code declined}.
 *
 * @param files            number of uploaded files
 * @param links            number of links; they have no grams or print time
 * @param declined         items the admin declined
 * @param completed        items that are completed
 * @param copies           sum of the quantities, links included
 * @param grams            filament over the files whose weight is known; {@code null} when none is
 * @param weight           how much of {@code grams} is exact; never exact while there are links
 * @param printSeconds     print time over the files whose time is known; {@code null} when none is
 * @param printTimePartial some files or links have no print time, so {@code printSeconds} is too low
 * @param fits             {@code false} when any file doesn't fit; {@code null} when no file has a fit check
 */
public record RequestTotalsDto(
        int files,
        int links,
        int declined,
        int completed,
        int copies,
        Double grams,
        WeightKind weight,
        Long printSeconds,
        boolean printTimePartial,
        Boolean fits) {
}
