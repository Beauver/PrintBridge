package com.beauver.printbridge.slicing.dto;

import java.util.List;

/** The process and filament profiles the slicer service offers for a printer and nozzle. */
public record SlicerProfilesDto(String machine, List<String> processes, List<String> filaments) {
}
