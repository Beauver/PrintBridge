package com.beauver.printbridge.modelinfo.dto;

/**
 * @param color {@code #RRGGBB} or {@code null}
 * @param grams exact usage from a sliced project, else {@code null}
 */
public record FilamentDto(String type, String color, Double grams) {
}
