package com.beauver.printbridge.slicing.client;

import java.util.List;

/** What the slicer service returns: one entry per requested plate. */
public record SliceResponse(List<Plate> plates) {

    /**
     * Either the numbers or, when OrcaSlicer refused the plate, its reason in {@code error}.
     *
     * @param seconds estimated print time
     */
    public record Plate(int index, Double grams, Long seconds, List<Filament> filaments, String error) {
    }

    public record Filament(String type, String color, Double grams) {
    }
}
