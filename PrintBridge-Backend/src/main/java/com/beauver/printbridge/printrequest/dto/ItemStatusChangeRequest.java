package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.printrequest.file.ItemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Move one file or link of a request to another status (#61; admin).
 *
 * @param declineReason optional, only kept when declining
 */
public record ItemStatusChangeRequest(
        @NotNull(message = "Choose a status")
        ItemStatus status,
        @Size(max = 500, message = "The reason must be at most 500 characters")
        String declineReason) {
}
