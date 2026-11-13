package com.beauver.printbridge.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * A printer to add or change. Nozzles and profiles may stay empty while server slicing is off.
 * See {@link PrinterDto} for the fields.
 */
public record PrinterInputDto(
        @NotBlank @Size(max = 200) String model,
        @Valid @NotNull BuildPlateDto buildPlate,
        @Size(max = 8) List<@NotNull @DecimalMin("0.05") @DecimalMax("5") BigDecimal> ownedNozzles,
        @DecimalMin("0.05") @DecimalMax("5") BigDecimal defaultNozzle,
        @Size(max = 200) String process,
        @Size(max = 200) String filament) {
}
