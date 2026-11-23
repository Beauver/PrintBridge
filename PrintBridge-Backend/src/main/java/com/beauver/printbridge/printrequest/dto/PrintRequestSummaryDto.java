package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.printrequest.RequestStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A row in a request list. {@code finalPrice} is null while the price is pending, {@code paidAt}
 * while not paid.
 *
 * @param itemsCompleted files and links that are completed (#61)
 * @param itemsActive    files and links that aren't declined
 * @param estimatedPrice the automatic price while there is no final price (#72); {@code null} otherwise
 * @param currency       ISO 4217 code of the final and estimated price (#75), e.g. EUR
 */
public record PrintRequestSummaryDto(
        Long id,
        String name,
        RequestStatus status,
        BigDecimal finalPrice,
        Instant paidAt,
        String ownerUsername,
        Instant createdAt,
        int itemsCompleted,
        int itemsActive,
        EstimatedPriceDto estimatedPrice,
        String currency) {
}
