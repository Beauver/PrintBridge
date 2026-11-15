package com.beauver.printbridge.slicing;

import com.beauver.printbridge.slicing.client.SlicerPrinter;
import com.beauver.printbridge.slicing.client.SlicerProfiles;
import com.beauver.printbridge.slicing.dto.SlicerPrinterDto;
import com.beauver.printbridge.slicing.dto.SlicerProfilesDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** What the slicer service answers, as our API returns it; keeps the two formats apart. */
@ApplicationScoped
public class SlicerMapper {

    public SlicerPrinterDto toDto(SlicerPrinter printer) {
        List<SlicerPrinterDto.NozzleDto> nozzles = printer.nozzles().stream().map(SlicerMapper::toDto).toList();
        return new SlicerPrinterDto(printer.model(), printer.vendor(), nozzles);
    }

    public SlicerProfilesDto toDto(SlicerProfiles profiles) {
        return new SlicerProfilesDto(profiles.machine(), profiles.processes(), profiles.filaments());
    }

    private static SlicerPrinterDto.NozzleDto toDto(SlicerPrinter.Nozzle nozzle) {
        SlicerPrinter.Bed bed = nozzle.bed();
        return new SlicerPrinterDto.NozzleDto(nozzle.diameter(), nozzle.machine(),
                new SlicerPrinterDto.BedDto(bed.x(), bed.y(), bed.z()));
    }
}
