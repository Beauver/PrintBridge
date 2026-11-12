package com.beauver.printbridge.modelinfo;

public enum ModelInfoStatus {
    /** Not analysed yet (no row): the startup backfill hasn't reached it. Never stored. */
    PENDING,
    ANALYZED,
    /** The analyser couldn't read the file. Not retried automatically. */
    FAILED
}
