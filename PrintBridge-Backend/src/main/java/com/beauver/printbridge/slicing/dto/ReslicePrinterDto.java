package com.beauver.printbridge.slicing.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * An owned printer the admin can re-slice on (#21).
 *
 * @param process  its process profile for the default nozzle
 * @param filament its filament profile
 */
public record ReslicePrinterDto(
        long id,
        String model,
        List<BigDecimal> ownedNozzles,
        BigDecimal defaultNozzle,
        String process,
        String filament) {
}
