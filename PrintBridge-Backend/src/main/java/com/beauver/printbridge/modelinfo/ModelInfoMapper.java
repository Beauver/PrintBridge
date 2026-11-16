package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelfile.analysis.FilamentUsage;
import com.beauver.printbridge.modelinfo.dto.FilamentDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.PlateDto;
import com.beauver.printbridge.modelinfo.dto.SlicingInfoDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Model details as the API returns them. The numbers are worked out by {@link ModelDetailsCalculator}. */
@ApplicationScoped
public class ModelInfoMapper {

    public ModelInfoDto toDto(ModelDetails details) {
        List<PlateDto> plates = details.plates().stream().map(ModelInfoMapper::toDto).toList();
        return new ModelInfoDto(details.status(), details.source(), details.volumeCm3(), details.totalGrams(),
                details.weight(), details.printSeconds(), details.fits(), details.fitPrinter(), plates,
                toDto(details.slicing()));
    }

    private static PlateDto toDto(PlateDetails plate) {
        List<FilamentDto> filaments = plate.filaments().stream().map(ModelInfoMapper::toDto).toList();
        return new PlateDto(plate.index(), plate.name(), plate.width(), plate.depth(), plate.height(), plate.grams(),
                plate.estimated(), plate.printSeconds(), plate.fits(), filaments, plate.sliceError());
    }

    private static FilamentDto toDto(FilamentUsage filament) {
        return new FilamentDto(filament.type(), filament.color(), filament.grams());
    }

    private static SlicingInfoDto toDto(SlicingDetails slicing) {
        return new SlicingInfoDto(slicing.enabled(), slicing.status(), slicing.printer(), slicing.nozzle(),
                slicing.madeForPrinter(), slicing.madeForNozzle(), slicing.error());
    }
}
