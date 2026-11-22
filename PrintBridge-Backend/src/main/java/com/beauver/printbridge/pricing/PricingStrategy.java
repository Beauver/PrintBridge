package com.beauver.printbridge.pricing;

import java.util.List;
import java.util.Optional;

/**
 * How a request's price is estimated from its items (#72). The admin's final price always wins;
 * this only fills the gap until it is set.
 */
public interface PricingStrategy {

    /** Empty when nothing could be priced (or automatic prices are off). */
    Optional<PriceEstimate> estimate(List<PricedItem> items);
}
