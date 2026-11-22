package com.beauver.printbridge.settings;

import java.math.BigDecimal;

/**
 * How the price estimate is made (#72), as it applies now.
 *
 * @param fromSpoolman     use the chosen filaments' Spoolman prices (Settings → Spoolman); off while the
 *                         integration is off
 * @param markup           multiplies every price
 * @param pricePer100Grams the default for everything without a Spoolman price; zero means none
 */
public record PricingSettings(boolean fromSpoolman, BigDecimal markup, BigDecimal pricePer100Grams) {
}
