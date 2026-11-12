package com.beauver.printbridge.modelinfo;

/**
 * Rough filament weight for models without slicer data: solid volume × density × the share of
 * the volume a typical print fills (walls plus sparse infill).
 */
final class FilamentEstimate {

    // TODO: #11 take the density from the chosen Spoolman filament instead of assuming PLA.
    static final double DENSITY_G_PER_CM3 = 1.24;
    // TODO: #11 revisit together with the Spoolman integration and pricing.
    static final double FILL_FACTOR = 0.35;

    private FilamentEstimate() {
    }

    static double grams(double volumeMm3) {
        return volumeMm3 / 1000 * DENSITY_G_PER_CM3 * FILL_FACTOR;
    }
}
