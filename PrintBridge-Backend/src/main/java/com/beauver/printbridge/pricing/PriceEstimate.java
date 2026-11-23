package com.beauver.printbridge.pricing;

import java.math.BigDecimal;

/**
 * An automatic price for a request (#72).
 *
 * @param amount  in the configured currency (#75), rounded to cents
 * @param partial some parts had no grams or price, so the real price is higher
 */
public record PriceEstimate(BigDecimal amount, boolean partial) {
}
