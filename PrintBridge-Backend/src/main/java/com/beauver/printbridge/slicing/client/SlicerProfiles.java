package com.beauver.printbridge.slicing.client;

import java.util.List;

/** Process and filament profiles OrcaSlicer offers for one printer + nozzle. */
public record SlicerProfiles(String machine, List<String> processes, List<String> filaments) {
}
