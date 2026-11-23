package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.Size;

/**
 * The requester cancels their request (#85).
 *
 * @param reason optional; blank means none
 */
public record CancelRequest(
        @Size(max = 2000, message = "Reason must be at most 2000 characters")
        String reason) {
}
