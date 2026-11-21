package com.beauver.printbridge.settings;

/**
 * The Spoolman switches as they apply now: both count as off while {@code SPOOLMAN_URL} isn't set,
 * whatever is saved.
 *
 * @param enabled        the integration is on: filaments and stock are read from Spoolman
 * @param colorSelection requesters may choose filaments from Spoolman
 */
public record SpoolmanSettings(boolean enabled, boolean colorSelection) {

    public static final SpoolmanSettings OFF = new SpoolmanSettings(false, false);
}
