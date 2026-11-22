package com.beauver.printbridge.pricing;

import com.beauver.printbridge.settings.PricingSettings;
import com.beauver.printbridge.settings.SettingsService;
import com.beauver.printbridge.spoolman.SpoolmanCatalog;
import com.beauver.printbridge.spoolman.SpoolmanFilament;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The price estimate of a request (#72) with the settings in Settings → Pricing: Spoolman's prices
 * with the price per 100 g as the default for colours without a priced filament, or the price per
 * 100 g for everything; either way times the markup.
 */
@ApplicationScoped
public class PricingService {

    @Inject
    SettingsService settings;

    @Inject
    SpoolmanCatalog catalog;

    public Optional<PriceEstimate> estimate(List<PricedItem> items) {
        return strategy().estimate(items);
    }

    /** The strategy that applies now; Spoolman prices count as unknown while Spoolman can't be read. */
    PricingStrategy strategy() {
        PricingSettings pricing = settings.getPricing();
        if (!pricing.fromSpoolman()) {
            return new PricePerHundredGrams(pricing.pricePer100Grams(), pricing.markup());
        }
        Map<Long, Double> pricePerGram = new HashMap<>();
        for (SpoolmanFilament filament : catalog.filamentsIfAvailable().orElse(List.of())) {
            Double price = filament.pricePerGram();
            if (price != null) {
                pricePerGram.put(filament.id(), price);
            }
        }
        return new SpoolmanPrices(pricing.markup(), pricePerGram, defaultPricePerGram(pricing));
    }

    /** The price per 100 g as a price per gram; {@code null} when it is zero (no default). */
    private static Double defaultPricePerGram(PricingSettings pricing) {
        if (pricing.pricePer100Grams().signum() <= 0) {
            return null;
        }
        return pricing.pricePer100Grams().doubleValue() / 100;
    }
}
