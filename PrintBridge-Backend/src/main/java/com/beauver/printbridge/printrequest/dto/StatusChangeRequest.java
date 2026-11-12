package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.printrequest.RequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Moves a request to another status. {@code declineReason} is only used when declining. */
public record StatusChangeRequest(
        @NotNull(message = "Status is required")
        RequestStatus status,

        @Size(max = 2000, message = "Decline reason must be at most 2000 characters")
        String declineReason) {
}
