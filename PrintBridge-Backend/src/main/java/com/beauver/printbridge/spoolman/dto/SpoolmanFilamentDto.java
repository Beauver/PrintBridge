package com.beauver.printbridge.spoolman.dto;

import java.util.List;

/**
 * A filament the requester can choose.
 *
 * @param color     {@code #RRGGBB}; {@code null} for multi-colour filaments or when not set in Spoolman
 * @param colors    the colours of a multi-colour filament; otherwise empty
 * @param gramsLeft weight left on its spools that aren't archived; {@code null} when unknown
 */
public record SpoolmanFilamentDto(
        long id,
        String name,
        String vendor,
        String material,
        String color,
        List<String> colors,
        Double gramsLeft) {
}
