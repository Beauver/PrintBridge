package com.beauver.printbridge.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server slicing settings. Printer, nozzles and profiles may stay empty while slicing is off.
 *
 * @param printer       printer model as OrcaSlicer names it (e.g. "Bambu Lab P1S")
 * @param ownedNozzles  nozzle sizes (mm) the admin can mount
 * @param defaultNozzle nozzle for STLs and for projects made for a nozzle that isn't owned
 * @param process       OrcaSlicer process profile for the default nozzle
 * @param filament      OrcaSlicer filament profile for the default nozzle
 */
public record SlicingSettingsDto(
        @NotNull Boolean enabled,
        @NotNull @Min(1) @Max(60) Integer timeoutMinutes,
        @Size(max = 200) String printer,
        @Size(max = 8) List<@NotNull @DecimalMin("0.05") @DecimalMax("5") BigDecimal> ownedNozzles,
        @DecimalMin("0.05") @DecimalMax("5") BigDecimal defaultNozzle,
        @Size(max = 200) String process,
        @Size(max = 200) String filament) {
}
