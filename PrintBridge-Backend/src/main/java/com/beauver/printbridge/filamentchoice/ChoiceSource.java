package com.beauver.printbridge.filamentchoice;

/** Who made a filament choice (#76). */
public enum ChoiceSource {
    /** A requester or the admin, in the picker. */
    MANUAL,
    /** Automatic matching: the closest Spoolman filament when the file's colours became known. */
    AUTO
}
