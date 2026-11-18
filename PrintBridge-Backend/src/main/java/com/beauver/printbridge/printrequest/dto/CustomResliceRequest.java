package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Re-slice one file with chosen settings (#21).
 *
 * @param printerId one of the owned printers
 * @param nozzle    a nozzle (mm) owned on that printer
 * @param process   process profile; {@code null} keeps the project's own settings
 * @param filament  filament profile; {@code null} keeps the project's filaments
 */
public record CustomResliceRequest(
        @NotNull(message = "Choose a printer")
        Long printerId,
        @NotNull(message = "Choose a nozzle")
        @DecimalMin(value = "0.01", message = "Choose a nozzle")
        BigDecimal nozzle,
        @Size(max = 200, message = "The process profile name is too long")
        String process,
        @Size(max = 200, message = "The filament profile name is too long")
        String filament) {
}
