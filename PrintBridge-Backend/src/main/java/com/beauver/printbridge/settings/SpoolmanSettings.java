package com.beauver.printbridge.settings;

/**
 * The Spoolman settings as they apply now: both switches count as off while {@code SPOOLMAN_URL}
 * isn't set, whatever is saved.
 *
 * @param enabled          the integration is on: filaments and stock are read from Spoolman
 * @param colorSelection   requesters may choose filaments from Spoolman
 * @param prices           the price estimate uses the chosen filaments' Spoolman prices (#72)
 * @param notEnoughMessage shown when a request needs more of a chosen filament than is left
 */
public record SpoolmanSettings(boolean enabled, boolean colorSelection, boolean prices, String notEnoughMessage) {

    /** The warning when the admin hasn't set their own (#11). */
    public static final String DEFAULT_NOT_ENOUGH_MESSAGE =
            "Not enough filament for this print, it will be ordered but your print might be delayed";

    public static final SpoolmanSettings OFF = new SpoolmanSettings(false, false, false, DEFAULT_NOT_ENOUGH_MESSAGE);
}
