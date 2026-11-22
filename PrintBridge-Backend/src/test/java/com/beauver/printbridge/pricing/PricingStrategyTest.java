package com.beauver.printbridge.pricing;

import com.beauver.printbridge.filamentchoice.ChoiceSource;
import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceDto;
import com.beauver.printbridge.modelfile.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.ModelInfoStatus;
import com.beauver.printbridge.modelinfo.WeightKind;
import com.beauver.printbridge.modelinfo.dto.FilamentDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.PlateDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PricingStrategyTest {

    private static ModelInfoDto info(Double totalGrams, PlateDto... plates) {
        return new ModelInfoDto(ModelInfoStatus.ANALYZED, ModelSource.BAMBU, 1.0, totalGrams, WeightKind.EXACT, null,
                null, null, List.of(plates), null);
    }

    private static PlateDto plate(int index, Double grams, FilamentDto... filaments) {
        return new PlateDto(index, null, 1.0, 1.0, 1.0, grams, false, null, null, List.of(filaments), null);
    }

    private static FilamentChoiceDto choice(Integer plate, String color, long filamentId, boolean orphaned) {
        String type = null;
        if (color != null) {
            type = "PLA";
        }
        return new FilamentChoiceDto(1L, plate == null, plate, type, color, filamentId, null, null, null, null,
                List.of(), Instant.EPOCH, orphaned, null, null, null, false, ChoiceSource.MANUAL);
    }

    private static Optional<PriceEstimate> perHundred(String price, PricedItem... items) {
        return new PricePerHundredGrams(new BigDecimal(price), BigDecimal.ONE).estimate(List.of(items));
    }

    @Test
    void pricePerHundredGramsOverAllGramsTimesQuantity() {
        PricedItem twoCopies = new PricedItem(2, false, info(40.0), List.of());
        PricedItem one = new PricedItem(1, false, info(10.0), List.of());
        PricedItem declined = new PricedItem(5, true, info(1000.0), List.of());

        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("2.25"), false)),
                perHundred("2.50", twoCopies, one, declined));
        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("4.50"), false)),
                new PricePerHundredGrams(new BigDecimal("2.50"), new BigDecimal("2")).estimate(List.of(twoCopies,
                        one)));
    }

    @Test
    void pricePerHundredGramsIsPartialWithoutGramsAndOffAtZero() {
        PricedItem known = new PricedItem(1, false, info(10.0), List.of());
        PricedItem link = new PricedItem(1, false, null, List.of());
        PricedItem pending = new PricedItem(1, false, ModelInfoDto.withStatus(ModelInfoStatus.PENDING, null),
                List.of());

        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("0.30"), true)), perHundred("3", known, link,
                pending));
        assertEquals(Optional.empty(), perHundred("3", link));
        assertEquals(Optional.empty(), perHundred("0", known));
        assertEquals(Optional.empty(), perHundred("3"));
    }

    @Test
    void spoolmanPricesEveryChosenColourTimesTheMarkup() {
        ModelInfoDto project = info(60.0,
                plate(1, 40.0, new FilamentDto("PLA", "#FFFFFF", 20.0), new FilamentDto("PLA", "#000000", 20.0)),
                plate(2, 20.0, new FilamentDto("PLA", "#FFFFFF", null)));
        // €25 per kg = 0.025 per gram; €50 per kg for the second filament.
        SpoolmanPrices prices = new SpoolmanPrices(new BigDecimal("1.5"), Map.of(7L, 0.025, 8L, 0.05), null);
        PricedItem item = new PricedItem(2, false, project, List.of(
                choice(1, "#FFFFFF", 7, false),
                choice(1, "#000000", 8, false),
                choice(2, "#ffffff", 7, false)));

        // (20 × 0.025 + 20 × 0.05 + 20 × 0.025) × 2 copies × 1.5 = 6.00
        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("6.00"), false)), prices.estimate(List.of(item)));
    }

    @Test
    void spoolmanPricesArePartialForWhatIsUnknown() {
        ModelInfoDto project = info(40.0,
                plate(1, 40.0, new FilamentDto("PLA", "#FFFFFF", 20.0), new FilamentDto("PLA", "#000000", null)));
        SpoolmanPrices prices = new SpoolmanPrices(BigDecimal.ONE, Map.of(7L, 0.1), null);

        // The same filament for both colours: the plate's grams count, though black's own are unknown.
        PricedItem bothChosen = new PricedItem(1, false, project,
                List.of(choice(1, "#FFFFFF", 7, false), choice(1, "#000000", 7, false)));
        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("4.00"), false)),
                prices.estimate(List.of(bothChosen)));
        // Different filaments: per colour, and black's grams are unknown.
        SpoolmanPrices twoPrices = new SpoolmanPrices(BigDecimal.ONE, Map.of(7L, 0.1, 8L, 0.2), null);
        PricedItem twoFilaments = new PricedItem(1, false, project,
                List.of(choice(1, "#FFFFFF", 7, false), choice(1, "#000000", 8, false)));
        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("2.00"), true)),
                twoPrices.estimate(List.of(twoFilaments)));

        // No choice for black, a filament without price, a choice the file no longer has, a link.
        ModelInfoDto sliced = info(40.0,
                plate(1, 40.0, new FilamentDto("PLA", "#FFFFFF", 20.0), new FilamentDto("PLA", "#000000", 20.0)));
        PricedItem missing = new PricedItem(1, false, sliced, List.of(choice(1, "#FFFFFF", 7, false)));
        PricedItem unpriced = new PricedItem(1, false, sliced, List.of(choice(1, "#000000", 99, false)));
        PricedItem orphaned = new PricedItem(1, false, sliced, List.of(choice(1, "#000000", 7, true)));
        PricedItem link = new PricedItem(1, false, null, List.of(choice(null, null, 7, false)));
        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("2.00"), true)),
                prices.estimate(List.of(missing, unpriced, orphaned, link)));

        // Nothing could be priced.
        assertEquals(Optional.empty(), prices.estimate(List.of(unpriced, link)));
        assertEquals(Optional.empty(), prices.estimate(List.of(new PricedItem(1, true, sliced,
                List.of(choice(1, "#FFFFFF", 7, false))))));
    }

    @Test
    void aWholeFileChoicePricesTheFileTotal() {
        PricedItem stl = new PricedItem(3, false, info(10.0, plate(1, 10.0)), List.of(choice(null, null, 7, false)));

        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("1.50"), false)),
                new SpoolmanPrices(BigDecimal.ONE, Map.of(7L, 0.05), null).estimate(List.of(stl)));
    }

    @Test
    void coloursWithoutAPricedFilamentUseTheDefaultPrice() {
        ModelInfoDto unsliced = info(40.0,
                plate(1, 40.0, new FilamentDto("PLA", "#FFFFFF", null), new FilamentDto("PLA", "#000000", null)));
        ModelInfoDto sliced = info(40.0,
                plate(1, 40.0, new FilamentDto("PLA", "#FFFFFF", 20.0), new FilamentDto("PLA", "#000000", 20.0)));
        // Default €2 per 100 g = 0.02 per gram; markup 2.
        SpoolmanPrices prices = new SpoolmanPrices(new BigDecimal("2"), Map.of(7L, 0.1), 0.02);

        // Nothing chosen on an unsliced plate: all on the default, so the plate's grams: 40 × 0.02 × 2.
        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("1.60"), false)),
                prices.estimate(List.of(new PricedItem(1, false, unsliced, List.of()))));
        // White chosen, black on the default: (20 × 0.1 + 20 × 0.02) × 2.
        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("4.80"), false)),
                prices.estimate(List.of(new PricedItem(1, false, sliced, List.of(choice(1, "#FFFFFF", 7, false))))));
        // A chosen filament without a price in Spoolman uses the default too.
        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("1.60"), false)),
                prices.estimate(List.of(new PricedItem(1, false, sliced, List.of(choice(1, "#FFFFFF", 99, false))))));
        // An empty plate costs nothing; a link is still unknown.
        ModelInfoDto withEmptyPlate = info(40.0, plate(1, 40.0, new FilamentDto("PLA", "#FFFFFF", 40.0)),
                plate(2, null));
        assertEquals(Optional.of(new PriceEstimate(new BigDecimal("1.60"), true)), prices.estimate(List.of(
                new PricedItem(1, false, withEmptyPlate, List.of()), new PricedItem(1, false, null, List.of()))));
    }
}
