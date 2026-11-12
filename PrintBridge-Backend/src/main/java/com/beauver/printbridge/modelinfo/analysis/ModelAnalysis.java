package com.beauver.printbridge.modelinfo.analysis;

import java.util.List;

/**
 * Result of reading a model file.
 *
 * @param volumeMm3 solid volume of everything that gets printed, in mm³
 */
public record ModelAnalysis(ModelSource source, double volumeMm3, List<PlateAnalysis> plates) {
}
