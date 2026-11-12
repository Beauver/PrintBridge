package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.NotNull;

/** Marks a request as paid ({@code true}) or not paid ({@code false}). */
public record PaidChangeRequest(
        @NotNull(message = "Paid is required")
        Boolean paid) {
}
