package com.beauver.printbridge.slicing.dto;

import java.math.BigDecimal;

/**
 * Where the "Re-slice with…" dialog starts (#21).
 *
 * @param printerId {@code null} when there is no printer to start from
 * @param process   {@code null}: the project's own settings (or nothing chosen yet)
 * @param filament  {@code null}: the project's filaments (or nothing chosen yet)
 * @param custom    whether this is the last custom choice rather than the automatic one
 */
public record ResliceChoiceDto(
        Long printerId,
        BigDecimal nozzle,
        String process,
        String filament,
        boolean custom) {
}
