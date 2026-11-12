package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/** Sets the final price in EUR; {@code null} clears it back to "price pending". */
public record PriceChangeRequest(
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Price must be at most 99999999.99 with at most 2 decimals")
        BigDecimal finalPrice) {
}
