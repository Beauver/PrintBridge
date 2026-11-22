package com.beauver.printbridge.settings;

/**
 * The Spoolman settings as they apply now: the switches count as off while {@code SPOOLMAN_URL}
 * isn't set, whatever is saved.
 *
 * @param enabled          the integration is on: filaments and stock are read from Spoolman
 * @param colorSelection   requesters may choose filaments from Spoolman
 * @param prices           the price estimate uses the chosen filaments' Spoolman prices (#72)
 * @param autoMatch        colours of new projects get the closest filament automatically (#76)
 * @param matchTolerance   the largest colour difference (CIEDE2000 ΔE) automatic matching accepts
 * @param notEnoughMessage shown when a request needs more of a chosen filament than is left
 * @param noMatchMessage   shown for colours without a close enough filament (#76)
 */
public record SpoolmanSettings(
        boolean enabled,
        boolean colorSelection,
        boolean prices,
        boolean autoMatch,
        int matchTolerance,
        String notEnoughMessage,
        String noMatchMessage) {

    /** The warning when the admin hasn't set their own (#11). */
    public static final String DEFAULT_NOT_ENOUGH_MESSAGE =
            "Not enough filament for this print, it will be ordered but your print might be delayed";

    /** The no-match warning when the admin hasn't set their own (#76). */
    public static final String DEFAULT_NO_MATCH_MESSAGE =
            "No close colour found, please select another colour for this print";

    /** ΔE "Normal": clearly the same colour family, small shade differences allowed. */
    public static final int DEFAULT_MATCH_TOLERANCE = 20;

    public static final SpoolmanSettings OFF = new SpoolmanSettings(false, false, false, false,
            DEFAULT_MATCH_TOLERANCE, DEFAULT_NOT_ENOUGH_MESSAGE, DEFAULT_NO_MATCH_MESSAGE);
}
