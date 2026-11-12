package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelinfo.analysis.Dimensions;
import com.beauver.printbridge.modelinfo.analysis.FilamentUsage;
import com.beauver.printbridge.modelinfo.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.analysis.PlateAnalysis;
import com.beauver.printbridge.modelinfo.dto.FilamentDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.PlateDto;
import com.beauver.printbridge.settings.BuildPlate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModelInfoMapperTest {

    final ModelInfoMapper mapper = new ModelInfoMapper();
    static final Optional<BuildPlate> PLATE = Optional.of(new BuildPlate(256, 256, 250));

    private static ModelInfo analyzed(List<PlateAnalysis> plates) {
        ModelInfo info = new ModelInfo();
        info.status = ModelInfoStatus.ANALYZED;
        info.source = ModelSource.BAMBU;
        info.volumeMm3 = 100_000.0;
        info.plates = plates;
        return info;
    }

    private static PlateAnalysis plate(int index, Dimensions size, Double grams, Long seconds) {
        return new PlateAnalysis(index, null, size, grams, seconds, List.of(new FilamentUsage("PLA", "#FFFFFF", grams)));
    }

    @Test
    void missingRowIsPending() {
        assertEquals(ModelInfoDto.withStatus(ModelInfoStatus.PENDING), mapper.toDto(null, PLATE));
    }

    @Test
    void failedAnalysisHasOnlyItsStatus() {
        ModelInfo info = new ModelInfo();
        info.status = ModelInfoStatus.FAILED;

        ModelInfoDto dto = mapper.toDto(info, PLATE);

        assertEquals(ModelInfoStatus.FAILED, dto.status());
        assertNull(dto.volumeCm3());
        assertTrue(dto.plates().isEmpty());
    }

    @Test
    void estimatesWeightAndChecksTheFit() {
        ModelInfoDto dto = mapper.toDto(analyzed(List.of(plate(1, new Dimensions(300, 10, 10), null, null),
                plate(2, new Dimensions(10, 10, 10), null, null))), PLATE);

        assertEquals(ModelSource.BAMBU, dto.source());
        assertEquals(100.0, dto.volumeCm3());
        assertEquals(43.0, dto.estimatedGrams());
        assertNull(dto.slicedGrams());
        assertNull(dto.printSeconds());
        assertFalse(dto.fits());
        assertEquals(List.of(false, true), dto.plates().stream().map(PlateDto::fits).toList());
        assertEquals(new PlateDto(2, null, 10.0, 10.0, 10.0, null, null, true,
                List.of(new FilamentDto("PLA", "#FFFFFF", null))), dto.plates().get(1));
    }

    @Test
    void addsUpSlicedPlatesAndIgnoresEmptyOnes() {
        ModelInfoDto dto = mapper.toDto(analyzed(List.of(plate(1, new Dimensions(10, 10, 10), 33.691, 2963L),
                plate(2, new Dimensions(10, 10, 10), 31.56, 2757L),
                plate(3, null, null, null))), PLATE);

        assertEquals(65.25, dto.slicedGrams());
        assertEquals(5720L, dto.printSeconds());
        assertTrue(dto.fits());
        PlateDto empty = dto.plates().get(2);
        assertNull(empty.width());
        assertNull(empty.fits());
    }

    @Test
    void noTotalsWhenOnlySomePlatesAreSliced() {
        ModelInfoDto dto = mapper.toDto(analyzed(List.of(plate(1, new Dimensions(10, 10, 10), 5.0, 60L),
                plate(2, new Dimensions(10, 10, 10), null, null))), PLATE);

        assertNull(dto.slicedGrams());
        assertNull(dto.printSeconds());
        assertEquals(5.0, dto.plates().getFirst().grams());
    }

    @Test
    void noFitCheckWithoutBuildPlate() {
        ModelInfoDto dto = mapper.toDto(analyzed(List.of(plate(1, new Dimensions(999, 999, 999), null, null))),
                Optional.empty());

        assertNull(dto.fits());
        assertNull(dto.plates().getFirst().fits());
    }
}
