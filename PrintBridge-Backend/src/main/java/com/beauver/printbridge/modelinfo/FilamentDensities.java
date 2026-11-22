package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelfile.analysis.FilamentUsage;

import java.util.List;

/**
 * The density to estimate a plate's grams with (#72), from the filament chosen for it. Handed in by
 * whoever knows the choices, so model info doesn't depend on them.
 */
@FunctionalInterface
public interface FilamentDensities {

    /** No choices known: every estimate uses {@link FilamentEstimate#DEFAULT_DENSITY_G_PER_CM3}. */
    FilamentDensities NONE = (plateIndex, filaments) -> null;

    /**
     * @param filaments the plate's colours as the file lists them; empty for a file without colours
     * @return g/cm³ for this plate's estimate, or {@code null} for the default
     */
    Double densityFor(int plateIndex, List<FilamentUsage> filaments);
}
