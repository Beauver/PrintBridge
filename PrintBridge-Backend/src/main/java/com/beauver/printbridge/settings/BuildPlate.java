package com.beauver.printbridge.settings;

import com.beauver.printbridge.modelinfo.analysis.Dimensions;

/** The printer's build volume in mm. */
public record BuildPlate(int x, int y, int z) {

    /** Slack for rounding: a model of exactly the plate size still fits. */
    private static final double TOLERANCE_MM = 0.01;

    /** Whether something of this size fits, as placed or turned 90° on the plate. */
    public boolean fits(Dimensions size) {
        if (size.height() > z + TOLERANCE_MM) {
            return false;
        }
        boolean asPlaced = size.width() <= x + TOLERANCE_MM && size.depth() <= y + TOLERANCE_MM;
        boolean turned = size.width() <= y + TOLERANCE_MM && size.depth() <= x + TOLERANCE_MM;
        return asPlaced || turned;
    }
}
