package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelinfo.analysis.FilamentUsage;
import com.beauver.printbridge.modelinfo.analysis.PlateAnalysis;
import com.beauver.printbridge.modelinfo.dto.FilamentDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.PlateDto;
import com.beauver.printbridge.modelinfo.dto.SlicingInfoDto;
import com.beauver.printbridge.modelinfo.dto.WeightKind;
import com.beauver.printbridge.settings.BuildPlate;
import com.beauver.printbridge.slicing.SliceResult;
import com.beauver.printbridge.slicing.SliceStatus;
import com.beauver.printbridge.slicing.SlicedPlate;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a stored analysis and server slicing result into what the detail page shows. Per plate
 * the numbers come from, in order: server slicing, the slice data saved in the file, an
 * estimate from the plate's volume. The fit check uses the current build plate, so changing
 * the plate size applies to existing requests too.
 */
@ApplicationScoped
public class ModelInfoMapper {

    /** What the mapper needs to know besides the stored rows. */
    public record Context(Optional<BuildPlate> buildPlate, boolean slicingEnabled, boolean admin) {
    }

    public ModelInfoDto toDto(ModelInfo info, SliceResult slice, Context context) {
        SlicingInfoDto slicing = slicingInfo(slice, context);
        if (info == null) {
            return ModelInfoDto.withStatus(ModelInfoStatus.PENDING, slicing);
        }
        if (info.status != ModelInfoStatus.ANALYZED) {
            return ModelInfoDto.withStatus(info.status, slicing);
        }
        Map<Integer, SlicedPlate> sliced = serverPlates(slice);
        boolean projectColours = slice != null && slice.madeForNozzle != null;
        List<PlateDto> plates = info.plates.stream()
                .map(plate -> toDto(plate, sliced.get(plate.index()), projectColours, context))
                .toList();

        List<PlateDto> filled = plates.stream().filter(plate -> plate.grams() != null).toList();
        Double totalGrams = null;
        WeightKind weight = null;
        if (!filled.isEmpty()) {
            totalGrams = round(filled.stream().mapToDouble(PlateDto::grams).sum(), 2);
            weight = weightKind(filled);
        }
        Long printSeconds = null;
        if (!filled.isEmpty() && filled.stream().allMatch(plate -> plate.printSeconds() != null)) {
            printSeconds = filled.stream().mapToLong(PlateDto::printSeconds).sum();
        }
        Boolean fits = null;
        if (context.buildPlate().isPresent()) {
            fits = plates.stream().allMatch(plate -> plate.fits() == null || plate.fits());
        }
        return new ModelInfoDto(ModelInfoStatus.ANALYZED, info.source, round(info.volumeMm3 / 1000, 1),
                totalGrams, weight, printSeconds, fits, plates, slicing);
    }

    private static SlicingInfoDto slicingInfo(SliceResult slice, Context context) {
        if (slice == null) {
            return new SlicingInfoDto(context.slicingEnabled(), null, null, null, null);
        }
        String error = null;
        if (context.admin()) {
            error = slice.error;
        }
        return new SlicingInfoDto(context.slicingEnabled(), slice.status, slice.nozzle, slice.madeForNozzle, error);
    }

    private static Map<Integer, SlicedPlate> serverPlates(SliceResult slice) {
        if (slice == null || slice.status != SliceStatus.DONE || slice.plates == null) {
            return Map.of();
        }
        return slice.plates.stream().collect(Collectors.toMap(SlicedPlate::index, Function.identity(),
                (first, second) -> first));
    }

    private static PlateDto toDto(PlateAnalysis plate, SlicedPlate server, boolean projectColours, Context context) {
        String sliceError = null;
        if (server != null && server.error() != null && context.admin()) {
            sliceError = server.error();
        }
        if (plate.size() == null) {
            return new PlateDto(plate.index(), plate.name(), null, null, null, null, null, null, null,
                    filaments(plate.filaments()), sliceError);
        }
        Boolean fits = context.buildPlate().map(buildPlate -> buildPlate.fits(plate.size())).orElse(null);
        Double grams;
        Long seconds;
        List<FilamentUsage> filaments;
        boolean estimated = false;
        if (server != null && server.error() == null && server.grams() != null) {
            grams = server.grams();
            seconds = server.seconds();
            filaments = mergeSame(server.filaments());
            if (projectColours) {
                // Sliced with the admin's filament for another nozzle: the project's colours tell more.
                filaments = withoutGrams(plate.filaments());
            }
        } else if (plate.grams() != null) {
            grams = plate.grams();
            seconds = plate.printSeconds();
            filaments = plate.filaments();
        } else {
            grams = (double) Math.round(FilamentEstimate.grams(plate.volumeMm3()));
            seconds = null;
            filaments = plate.filaments();
            estimated = true;
        }
        return new PlateDto(plate.index(), plate.name(), plate.size().width(), plate.size().depth(),
                plate.size().height(), grams, estimated, seconds, fits, filaments(filaments), sliceError);
    }

    private static WeightKind weightKind(List<PlateDto> filled) {
        long estimated = filled.stream().filter(plate -> Boolean.TRUE.equals(plate.estimated())).count();
        if (estimated == 0) {
            return WeightKind.EXACT;
        }
        if (estimated == filled.size()) {
            return WeightKind.ESTIMATED;
        }
        return WeightKind.PARTLY_ESTIMATED;
    }

    /** One entry per type and colour; OrcaSlicer lists each slot separately. */
    private static List<FilamentUsage> mergeSame(List<FilamentUsage> filaments) {
        Map<List<String>, FilamentUsage> merged = new LinkedHashMap<>();
        for (FilamentUsage filament : filaments) {
            List<String> key = new ArrayList<>();
            key.add(filament.type());
            key.add(filament.color());
            merged.merge(key, filament, (first, second) -> new FilamentUsage(first.type(), first.color(),
                    sum(first.grams(), second.grams())));
        }
        return List.copyOf(merged.values());
    }

    private static Double sum(Double first, Double second) {
        if (first == null || second == null) {
            return Objects.requireNonNullElse(first, second);
        }
        return round(first + second, 2);
    }

    private static List<FilamentUsage> withoutGrams(List<FilamentUsage> filaments) {
        return filaments.stream().map(filament -> new FilamentUsage(filament.type(), filament.color(), null)).toList();
    }

    private static List<FilamentDto> filaments(List<FilamentUsage> filaments) {
        return filaments.stream()
                .map(filament -> new FilamentDto(filament.type(), filament.color(), filament.grams()))
                .toList();
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
