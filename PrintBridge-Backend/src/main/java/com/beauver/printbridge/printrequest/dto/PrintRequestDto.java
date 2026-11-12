package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.printrequest.RequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PrintRequestDto(
        Long id,
        String name,
        String description,
        RequestStatus status,
        BigDecimal finalPrice,
        String declineReason,
        Long ownerId,
        String ownerUsername,
        Instant createdAt,
        Instant updatedAt,
        List<RequestFileDto> files) {
}
