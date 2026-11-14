package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.printrequest.RequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A request with its files.
 *
 * @param paidAt       when it was marked paid; {@code null} while not paid
 * @param totals       all files times their quantities
 * @param nextStatuses statuses the admin may move this request to; empty once it is final
 */
public record PrintRequestDto(
        Long id,
        String name,
        String description,
        RequestStatus status,
        BigDecimal finalPrice,
        String declineReason,
        Instant paidAt,
        Long ownerId,
        String ownerUsername,
        Instant createdAt,
        Instant updatedAt,
        List<RequestFileDto> files,
        RequestTotalsDto totals,
        List<RequestStatus> nextStatuses) {
}
