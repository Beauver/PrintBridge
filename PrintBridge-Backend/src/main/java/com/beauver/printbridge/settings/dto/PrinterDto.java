package com.beauver.printbridge.settings.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * An owned printer.
 *
 * @param model         printer model as OrcaSlicer names it (e.g. "Bambu Lab P1S"), or the admin's own name
 * @param ownedNozzles  nozzle sizes (mm) the admin can mount on it
 * @param defaultNozzle nozzle for STLs and for projects made for a nozzle that isn't owned
 * @param process       OrcaSlicer process profile for the default nozzle
 * @param filament      OrcaSlicer filament profile for the default nozzle
 */
public record PrinterDto(
        long id,
        String model,
        BuildPlateDto buildPlate,
        List<BigDecimal> ownedNozzles,
        BigDecimal defaultNozzle,
        String process,
        String filament) {
}
