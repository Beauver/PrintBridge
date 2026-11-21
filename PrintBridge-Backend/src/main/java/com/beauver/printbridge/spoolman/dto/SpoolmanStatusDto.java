package com.beauver.printbridge.spoolman.dto;

/**
 * Whether Spoolman can be used, for the settings page.
 *
 * @param configured {@code SPOOLMAN_URL} is set
 * @param reachable  Spoolman answered
 * @param url        the configured address; {@code null} when not set
 * @param version    Spoolman's version when reachable
 * @param message    why it can't be used, otherwise {@code null}
 */
public record SpoolmanStatusDto(
        boolean configured,
        boolean reachable,
        String url,
        String version,
        String message) {
}
