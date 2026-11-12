package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.printrequest.RequestStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** A row in a request list. {@code finalPrice} is null while the price is pending. */
public record PrintRequestSummaryDto(
        Long id,
        String name,
        RequestStatus status,
        BigDecimal finalPrice,
        String ownerUsername,
        Instant createdAt) {
}
