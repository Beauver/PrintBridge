package com.beauver.printbridge.filamentchoice.dto;

/**
 * A colour of a plate as the model info lists it.
 *
 * @param originalType  {@code null} when the file doesn't say
 * @param originalColor {@code #RRGGBB}
 */
public record ColorSlotDto(int plateIndex, String originalType, String originalColor) {
}
