package com.beauver.printbridge.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * A fixed price per 100 g over all grams of the request, whatever the filament, times the markup
 * (#72). A price of zero means no automatic price.
 */
public class PricePerHundredGrams implements PricingStrategy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal pricePer100Grams;
    private final BigDecimal markup;

    public PricePerHundredGrams(BigDecimal pricePer100Grams, BigDecimal markup) {
        this.pricePer100Grams = pricePer100Grams;
        this.markup = markup;
    }

    /** Every item's total grams times its quantity; links and items without known grams make it partial. */
    @Override
    public Optional<PriceEstimate> estimate(List<PricedItem> items) {
        if (pricePer100Grams.signum() <= 0) {
            return Optional.empty();
        }
        double grams = 0;
        boolean priced = false;
        boolean partial = false;
        for (PricedItem item : items) {
            if (item.declined()) {
                continue;
            }
            Double itemGrams = null;
            if (item.info() != null) {
                itemGrams = item.info().totalGrams();
            }
            if (itemGrams == null) {
                partial = true;
                continue;
            }
            grams += itemGrams * item.quantity();
            priced = true;
        }
        if (!priced) {
            return Optional.empty();
        }
        BigDecimal amount = BigDecimal.valueOf(grams)
                .multiply(pricePer100Grams)
                .multiply(markup)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return Optional.of(new PriceEstimate(amount, partial));
    }
}
