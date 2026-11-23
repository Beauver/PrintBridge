package com.beauver.printbridge.settings.dto;

import com.beauver.printbridge.common.validation.CurrencyCode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * The price settings (#72, #75). Whether Spoolman's prices are used is a Spoolman setting.
 *
 * @param currency         ISO 4217 code of every price (e.g. EUR); stored upper case. Amounts are never converted.
 * @param markup           multiplies every price (e.g. 1.5)
 * @param pricePer100Grams price per 100 g in the currency: the default for everything without a Spoolman price;
 *                         0 means none
 */
public record PricingSettingsDto(
        @NotNull(message = "Currency is required")
        @CurrencyCode(message = "Currency must be an ISO 4217 code such as EUR or USD")
        String currency,
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
