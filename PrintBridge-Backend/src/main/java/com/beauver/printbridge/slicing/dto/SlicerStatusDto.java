package com.beauver.printbridge.slicing.dto;

/**
 * Whether the slicer service can be used.
 *
 * @param configured   {@code SLICER_URL} is set
 * @param reachable    the service answered
 * @param orcaVersion  OrcaSlicer's version when reachable
 * @param message      why it can't be used, otherwise {@code null}
 */
public record SlicerStatusDto(boolean configured, boolean reachable, String orcaVersion, String message) {
}
