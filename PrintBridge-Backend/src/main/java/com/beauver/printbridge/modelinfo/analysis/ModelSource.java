package com.beauver.printbridge.modelinfo.analysis;

/** Where the plate data of an analysed model came from. */
public enum ModelSource {
    /** Only geometry: an STL or a plain 3MF, treated as a single plate. */
    MESH,
    /** A Bambu Studio or OrcaSlicer project with plates and, if sliced, exact grams and time. */
    BAMBU
}
