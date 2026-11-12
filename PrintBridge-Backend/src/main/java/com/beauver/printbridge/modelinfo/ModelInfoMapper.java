package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelinfo.analysis.FilamentUsage;
import com.beauver.printbridge.modelinfo.analysis.PlateAnalysis;
import com.beauver.printbridge.modelinfo.dto.FilamentDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.PlateDto;
import com.beauver.printbridge.settings.BuildPlate;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Turns a stored analysis into what the detail page shows. The fit check is done here, against
 * the current build plate, so changing the plate size applies to existing requests too.
 */
@ApplicationScoped
public class ModelInfoMapper {

    public ModelInfoDto toDto(ModelInfo info, Optional<BuildPlate> buildPlate) {
        if (info == null) {
            return ModelInfoDto.withStatus(ModelInfoStatus.PENDING);
        }
        if (info.status != ModelInfoStatus.ANALYZED) {
            return ModelInfoDto.withStatus(info.status);
        }
        List<PlateDto> plates = info.plates.stream().map(plate -> toDto(plate, buildPlate)).toList();
        List<PlateAnalysis> filled = info.plates.stream().filter(plate -> plate.size() != null).toList();
        boolean allSliced = !filled.isEmpty() && filled.stream().allMatch(plate -> plate.grams() != null);
        boolean allTimed = !filled.isEmpty() && filled.stream().allMatch(plate -> plate.printSeconds() != null);

        Double slicedGrams = null;
        if (allSliced) {
            slicedGrams = round(filled.stream().mapToDouble(PlateAnalysis::grams).sum(), 2);
        }
        Long printSeconds = null;
        if (allTimed) {
            printSeconds = filled.stream().mapToLong(PlateAnalysis::printSeconds).sum();
        }
        Boolean fits = null;
        if (buildPlate.isPresent()) {
            fits = filled.stream().allMatch(plate -> buildPlate.get().fits(plate.size()));
        }
        return new ModelInfoDto(ModelInfoStatus.ANALYZED, info.source, round(info.volumeMm3 / 1000, 1),
                (double) Math.round(FilamentEstimate.grams(info.volumeMm3)), slicedGrams, printSeconds, fits, plates);
    }

    private static PlateDto toDto(PlateAnalysis plate, Optional<BuildPlate> buildPlate) {
        List<FilamentDto> filaments = plate.filaments().stream().map(ModelInfoMapper::toDto).toList();
        if (plate.size() == null) {
            return new PlateDto(plate.index(), plate.name(), null, null, null, plate.grams(), plate.printSeconds(),
                    null, filaments);
        }
        Boolean fits = null;
        if (buildPlate.isPresent()) {
            fits = buildPlate.get().fits(plate.size());
        }
        return new PlateDto(plate.index(), plate.name(), plate.size().width(), plate.size().depth(),
                plate.size().height(), plate.grams(), plate.printSeconds(), fits, filaments);
    }

    private static FilamentDto toDto(FilamentUsage filament) {
        return new FilamentDto(filament.type(), filament.color(), filament.grams());
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
