package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * The price estimate settings (#72). Whether Spoolman's prices are used is a Spoolman setting.
 *
 * @param markup           multiplies every price (e.g. 1.5)
 * @param pricePer100Grams EUR per 100 g: the default for everything without a Spoolman price; 0 means none
 */
public record PricingSettingsDto(
        @NotNull(message = "Markup is required")
        @DecimalMin(value = "0.01", message = "Markup must be at least 0.01")
        @DecimalMax(value = "100", message = "Markup must be at most 100")
        @Digits(integer = 3, fraction = 2, message = "Markup can have at most 2 decimals")
        BigDecimal markup,
        @NotNull(message = "Price per 100 g is required")
        @DecimalMin(value = "0", message = "Price per 100 g can't be negative")
        @DecimalMax(value = "1000", message = "Price per 100 g must be at most 1000")
        @Digits(integer = 4, fraction = 2, message = "Price per 100 g can have at most 2 decimals")
        BigDecimal pricePer100Grams) {
}
