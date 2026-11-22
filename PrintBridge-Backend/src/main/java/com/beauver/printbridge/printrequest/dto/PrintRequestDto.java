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
 * @param filamentSelection the caller may choose filaments from Spoolman now (#11): colour selection is on
 *                          and the request may still change for them
 * @param notEnoughFilamentMessage what to show when a chosen filament runs short (#11; set by the admin)
 * @param noMatchMessage           what to show for colours without a close filament (#76; set by the admin)
 * @param estimatedPrice           the automatic price while there is no final price (#72); {@code null} when
 *                                 there is a final price, the request is declined or nothing could be priced
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
        List<RequestStatus> nextStatuses,
        boolean filamentSelection,
        String notEnoughFilamentMessage,
        String noMatchMessage,
        EstimatedPriceDto estimatedPrice) {
}
