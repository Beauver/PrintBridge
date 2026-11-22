package com.beauver.printbridge.modelinfo;

/**
 * Rough filament weight for plates without slicer data (slicing off, queued, failed or refused):
 * solid volume × density × the share of the volume a typical print fills (walls plus sparse
 * infill). The density is the chosen Spoolman filament's when known (#72), else PLA's.
 */
public final class FilamentEstimate {

    /** PLA, for plates without a chosen filament with a known density. */
    public static final double DEFAULT_DENSITY_G_PER_CM3 = 1.24;
    static final double FILL_FACTOR = 0.35;

    private FilamentEstimate() {
    }

    /** @param density g/cm³; {@code null} for {@link #DEFAULT_DENSITY_G_PER_CM3} */
    static double grams(double volumeMm3, Double density) {
        double gramsPerCm3 = DEFAULT_DENSITY_G_PER_CM3;
        if (density != null) {
            gramsPerCm3 = density;
        }
        return volumeMm3 / 1000 * gramsPerCm3 * FILL_FACTOR;
    }
}
