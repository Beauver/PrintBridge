package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** How many copies of a file to print. */
public record QuantityChangeRequest(
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 100, message = "Quantity must be at most 100")
        Integer quantity) {
}
