package com.beauver.printbridge.slicing;

public enum SliceStatus {
    QUEUED,
    RUNNING,
    /** Sliced; individual plates may still have been refused (see {@link SlicedPlate#error()}). */
    DONE,
    /** The whole job failed: slicer unreachable, timed out, file missing, ... */
    FAILED;

    public boolean isPending() {
        return this == QUEUED || this == RUNNING;
    }
}
