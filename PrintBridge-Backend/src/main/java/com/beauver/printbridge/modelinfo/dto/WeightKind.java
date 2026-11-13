package com.beauver.printbridge.modelinfo.dto;

/** Where the grams come from. */
public enum WeightKind {
    /** Sliced: by the server or in the file itself. */
    EXACT,
    /** Some plates sliced, others estimated from their volume. */
    PARTLY_ESTIMATED,
    /** Estimated from the volume only. */
    ESTIMATED
}
