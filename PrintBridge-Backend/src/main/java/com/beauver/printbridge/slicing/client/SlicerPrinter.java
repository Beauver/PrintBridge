package com.beauver.printbridge.slicing.client;

import java.math.BigDecimal;
import java.util.List;

/** A printer model OrcaSlicer has profiles for, with its nozzle sizes. */
public record SlicerPrinter(String model, String vendor, List<Nozzle> nozzles) {

    /**
     * @param machine OrcaSlicer's machine profile for this printer and nozzle
     * @param bed     build volume in whole mm
     */
    public record Nozzle(BigDecimal diameter, String machine, Bed bed) {
    }

    public record Bed(int x, int y, int z) {
    }
}
