package com.beauver.printbridge.printrequest.dto;

import java.math.BigDecimal;

/**
 * The automatic price of a request until the admin sets the final price (#72).
 *
 * @param amount  in the configured currency (#75), rounded to cents
 * @param partial some parts had no grams or price, so the real price is higher
 */
public record EstimatedPriceDto(BigDecimal amount, boolean partial) {
}
