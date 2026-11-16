package com.beauver.printbridge.slicing.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A printer model the slicer service has profiles for.
 *
 * @param nozzles the nozzle sizes it can slice for, ascending
 */
public record SlicerPrinterDto(String model, String vendor, List<NozzleDto> nozzles) {

    /** @param machine name of the slicer's machine profile for this nozzle */
    public record NozzleDto(BigDecimal diameter, String machine, BedDto bed) {
    }

    /** Build volume in whole mm. */
    public record BedDto(int x, int y, int z) {
    }
}
