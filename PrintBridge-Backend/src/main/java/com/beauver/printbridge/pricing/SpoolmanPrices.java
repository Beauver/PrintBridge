package com.beauver.printbridge.pricing;

import com.beauver.printbridge.filamentchoice.FilamentSlot;
import com.beauver.printbridge.filamentchoice.FilamentSlots;
import com.beauver.printbridge.filamentchoice.dto.FilamentChoiceDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.PlateDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every colour priced with the filament chosen for it (#72): grams × the filament's price per gram
 * in Spoolman (spool price ÷ net weight). Colours without a choice, and chosen filaments without a
 * price in Spoolman, use the default price per gram (the price per 100 g setting). Everything is
 * multiplied by the markup.
 */
public class SpoolmanPrices implements PricingStrategy {

    private final BigDecimal markup;
    private final Map<Long, Double> pricePerGram;
    private final Double defaultPricePerGram;

    /**
     * @param pricePerGram        by Spoolman filament id; filaments without a price are left out
     * @param defaultPricePerGram for colours without a (priced) choice; {@code null} for none
     */
    public SpoolmanPrices(BigDecimal markup, Map<Long, Double> pricePerGram, Double defaultPricePerGram) {
        this.markup = markup;
        this.pricePerGram = pricePerGram;
        this.defaultPricePerGram = defaultPricePerGram;
    }

    /** Running total over the items; partial once something couldn't be priced. */
    private static final class Total {
        double cost;
        boolean priced;
        boolean partial;

        void add(Double grams, Double price, int quantity) {
            if (grams == null || price == null) {
                partial = true;
                return;
            }
            cost += grams * price * quantity;
            priced = true;
        }
    }

    /** Links have no grams and make the estimate partial; choices the file no longer has don't count. */
    @Override
    public Optional<PriceEstimate> estimate(List<PricedItem> items) {
        Total total = new Total();
        for (PricedItem item : items) {
            if (item.declined()) {
                continue;
            }
            price(item, total);
        }
        if (!total.priced) {
            return Optional.empty();
        }
        BigDecimal amount = BigDecimal.valueOf(total.cost).multiply(markup).setScale(2, RoundingMode.HALF_UP);
        return Optional.of(new PriceEstimate(amount, total.partial));
    }

    private void price(PricedItem item, Total total) {
        List<FilamentSlot> slots = FilamentSlots.of(item.info());
        FilamentSlot first = slots.getFirst();
        if (first.isWholeItem()) {
            total.add(first.grams(), priceFor(first, item.choices()), item.quantity());
            return;
        }
        for (PlateDto plate : plates(item.info())) {
            pricePlate(plate, item, total);
        }
    }

    /**
     * When every colour of the plate costs the same (all on the default, or one filament for all),
     * the plate's grams count, so a plate without per-colour grams can still be priced; otherwise
     * each colour with its own grams.
     */
    private void pricePlate(PlateDto plate, PricedItem item, Total total) {
        List<FilamentSlot> slots = new ArrayList<>();
        List<Double> prices = new ArrayList<>();
        for (FilamentSlot slot : FilamentSlots.of(singlePlate(item.info(), plate))) {
            slots.add(slot);
            prices.add(priceFor(slot, item.choices()));
        }
        Double shared = prices.getFirst();
        boolean samePrice = shared != null && prices.stream().allMatch(shared::equals);
        if (samePrice && plate.grams() != null) {
            total.add(plate.grams(), shared, item.quantity());
            return;
        }
        for (int i = 0; i < slots.size(); i++) {
            total.add(slots.get(i).grams(), prices.get(i), item.quantity());
        }
    }

    /** The price per gram of a colour: its chosen filament's, else the default; {@code null} for neither. */
    private Double priceFor(FilamentSlot slot, List<FilamentChoiceDto> choices) {
        Optional<FilamentChoiceDto> choice = choices.stream()
                .filter(candidate -> !candidate.orphaned())
                .filter(candidate -> slot.matches(candidate.plateIndex(), candidate.originalType(),
                        candidate.originalColor()))
                .findFirst();
        if (choice.isPresent()) {
            Double price = pricePerGram.get(choice.get().spoolmanFilamentId());
            if (price != null) {
                return price;
            }
        }
        return defaultPricePerGram;
    }

    /** The plates with colours; empty plates have nothing to print. */
    private static List<PlateDto> plates(ModelInfoDto info) {
        return info.plates().stream().filter(plate -> !plate.filaments().isEmpty()).toList();
    }

    /** The model info narrowed to one plate, so the slots (and their grams) are that plate's. */
    private static ModelInfoDto singlePlate(ModelInfoDto info, PlateDto plate) {
        return new ModelInfoDto(info.status(), info.source(), info.volumeCm3(), plate.grams(), info.weight(),
                info.printSeconds(), info.fits(), info.fitPrinter(), List.of(plate), info.slicing());
    }
}
