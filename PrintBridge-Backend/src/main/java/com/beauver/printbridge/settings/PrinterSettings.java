package com.beauver.printbridge.settings;

import java.math.BigDecimal;
import java.util.List;

/**
 * An owned printer as the slicing and model info code use it.
 *
 * @param ownedNozzles nozzle sizes in mm, without trailing zeros
 */
public record PrinterSettings(
        long id,
        String model,
        BuildPlate buildPlate,
        List<BigDecimal> ownedNozzles,
        BigDecimal defaultNozzle,
        String process,
        String filament) {

    public boolean owns(BigDecimal nozzle) {
        return nozzle != null && ownedNozzles.stream().anyMatch(owned -> owned.compareTo(nozzle) == 0);
    }

    /** Whether server slicing can use it: nozzles, a default nozzle and both profiles are set. */
    public boolean readyForSlicing() {
        return !ownedNozzles.isEmpty() && defaultNozzle != null && process != null && filament != null;
    }

    public boolean isModel(String otherModel) {
        return otherModel != null && model.equalsIgnoreCase(otherModel.trim());
    }
}
