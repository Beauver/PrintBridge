package com.beauver.printbridge.settings;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * The server slicing settings as the slicing code uses them.
 *
 * @param ownedNozzles nozzle sizes in mm, without trailing zeros
 */
public record SlicingSettings(
        boolean enabled,
        Duration timeout,
        String printer,
        List<BigDecimal> ownedNozzles,
        BigDecimal defaultNozzle,
        String process,
        String filament) {

    public boolean owns(BigDecimal nozzle) {
        return nozzle != null && ownedNozzles.stream().anyMatch(owned -> owned.compareTo(nozzle) == 0);
    }
}
