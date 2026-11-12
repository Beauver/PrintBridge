package com.beauver.printbridge.modelinfo;

import com.beauver.printbridge.modelinfo.analysis.Dimensions;
import com.beauver.printbridge.modelinfo.analysis.FilamentUsage;
import com.beauver.printbridge.modelinfo.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.analysis.PlateAnalysis;
import com.beauver.printbridge.modelinfo.dto.FilamentDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.PlateDto;
import com.beauver.printbridge.modelinfo.dto.WeightKind;
import com.beauver.printbridge.settings.BuildPlate;
import com.beauver.printbridge.slicing.SliceResult;
import com.beauver.printbridge.slicing.SliceStatus;
import com.beauver.printbridge.slicing.SlicedPlate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModelInfoMapperTest {

    final ModelInfoMapper mapper = new ModelInfoMapper();
    static final ModelInfoMapper.Context USER = new ModelInfoMapper.Context(
            Optional.of(new BuildPlate(256, 256, 250)), true, false);
    static final ModelInfoMapper.Context ADMIN = new ModelInfoMapper.Context(
            Optional.of(new BuildPlate(256, 256, 250)), true, true);
    static final Dimensions SMALL = new Dimensions(10, 10, 10);
    static final FilamentUsage WHITE = new FilamentUsage("PLA", "#FFFFFF", null);

    private static ModelInfo analyzed(PlateAnalysis... plates) {
        ModelInfo info = new ModelInfo();
        info.status = ModelInfoStatus.ANALYZED;
        info.source = ModelSource.BAMBU;
        info.volumeMm3 = 100_000.0;
        info.plates = List.of(plates);
        return info;
    }

    /** A plate of 10 cm³ (estimate 10 × 1.24 × 0.35 = 4.34 → 4 g), optionally with the file's own slice data. */
    private static PlateAnalysis plate(int index, Dimensions size, Double grams, Long seconds) {
        return new PlateAnalysis(index, null, size, 10_000, grams, seconds, List.of(WHITE));
    }

    private static SliceResult done(BigDecimal madeFor, SlicedPlate... plates) {
        SliceResult slice = new SliceResult();
        slice.status = SliceStatus.DONE;
        slice.nozzle = new BigDecimal("0.4");
        slice.madeForNozzle = madeFor;
        slice.plates = List.of(plates);
        return slice;
    }

    @Test
    void missingRowIsPendingAndCarriesSlicing() {
        ModelInfoDto dto = mapper.toDto(null, null, USER);

        assertEquals(ModelInfoStatus.PENDING, dto.status());
        assertTrue(dto.slicing().enabled());
        assertNull(dto.slicing().status());
    }

    @Test
    void failedAnalysisHasOnlyItsStatus() {
        ModelInfo info = new ModelInfo();
        info.status = ModelInfoStatus.FAILED;

        ModelInfoDto dto = mapper.toDto(info, null, USER);

        assertEquals(ModelInfoStatus.FAILED, dto.status());
        assertNull(dto.volumeCm3());
        assertTrue(dto.plates().isEmpty());
    }

    @Test
    void estimatesEachPlateFromItsVolume() {
        ModelInfoDto dto = mapper.toDto(analyzed(plate(1, new Dimensions(300, 10, 10), null, null),
                plate(2, SMALL, null, null)), null, USER);

        assertEquals(100.0, dto.volumeCm3());
        assertEquals(8.0, dto.totalGrams());
        assertEquals(WeightKind.ESTIMATED, dto.weight());
        assertNull(dto.printSeconds());
        assertFalse(dto.fits());
        assertEquals(List.of(false, true), dto.plates().stream().map(PlateDto::fits).toList());
        assertEquals(new PlateDto(2, null, 10.0, 10.0, 10.0, 4.0, true, null, true,
                List.of(new FilamentDto("PLA", "#FFFFFF", null)), null), dto.plates().get(1));
    }

    @Test
    void usesTheFilesOwnSliceData() {
        ModelInfoDto dto = mapper.toDto(analyzed(plate(1, SMALL, 33.691, 2963L), plate(2, SMALL, 31.56, 2757L),
                plate(3, null, null, null)), null, USER);

        assertEquals(65.25, dto.totalGrams());
        assertEquals(WeightKind.EXACT, dto.weight());
        assertEquals(5720L, dto.printSeconds());
        assertTrue(dto.fits());
        PlateDto empty = dto.plates().get(2);
        assertNull(empty.grams());
        assertNull(empty.estimated());
        assertNull(empty.fits());
    }

    @Test
    void serverNumbersWinAndSameFilamentsAreMerged() {
        SlicedPlate sliced = new SlicedPlate(1, 40.0, 3000L, List.of(new FilamentUsage("PLA", "#FFFFFF", 10.0),
                new FilamentUsage("PLA", "#FFFFFF", 25.0), new FilamentUsage("PETG", "#FF0000", 5.0)), null);

        ModelInfoDto dto = mapper.toDto(analyzed(plate(1, SMALL, 33.0, 2900L)), done(null, sliced), USER);

        PlateDto plate = dto.plates().getFirst();
        assertEquals(40.0, plate.grams());
        assertEquals(3000L, plate.printSeconds());
        assertFalse(plate.estimated());
        assertEquals(List.of(new FilamentDto("PLA", "#FFFFFF", 35.0), new FilamentDto("PETG", "#FF0000", 5.0)),
                plate.filaments());
        assertEquals(SliceStatus.DONE, dto.slicing().status());
        assertEquals(new BigDecimal("0.4"), dto.slicing().nozzle());
    }

    @Test
    void refusedPlateFallsBackToItsEstimate() {
        SliceResult slice = done(null, new SlicedPlate(1, 20.0, 1000L, List.of(), null),
                new SlicedPlate(2, null, null, List.of(), "Object conflicts"));

        ModelInfoDto user = mapper.toDto(analyzed(plate(1, SMALL, null, null), plate(2, SMALL, null, null)), slice, USER);
        ModelInfoDto admin = mapper.toDto(analyzed(plate(1, SMALL, null, null), plate(2, SMALL, null, null)), slice, ADMIN);

        assertEquals(24.0, user.totalGrams());
        assertEquals(WeightKind.PARTLY_ESTIMATED, user.weight());
        assertNull(user.printSeconds());
        assertTrue(user.plates().get(1).estimated());
        assertNull(user.plates().get(1).sliceError());
        assertEquals("Object conflicts", admin.plates().get(1).sliceError());
    }

    @Test
    void otherNozzleShowsTheProjectsColours() {
        SliceResult slice = done(new BigDecimal("0.8"),
                new SlicedPlate(1, 20.0, 1000L, List.of(new FilamentUsage("PLA", "#F2754E", 20.0)), null));

        ModelInfoDto dto = mapper.toDto(analyzed(plate(1, SMALL, null, null)), slice, USER);

        assertEquals(List.of(new FilamentDto("PLA", "#FFFFFF", null)), dto.plates().getFirst().filaments());
        assertEquals(new BigDecimal("0.8"), dto.slicing().madeForNozzle());
    }

    @Test
    void ignoresServerPlatesUntilDone() {
        SliceResult running = done(null, new SlicedPlate(1, 99.0, 1L, List.of(), null));
        running.status = SliceStatus.RUNNING;

        ModelInfoDto dto = mapper.toDto(analyzed(plate(1, SMALL, null, null)), running, USER);

        assertEquals(4.0, dto.totalGrams());
        assertEquals(SliceStatus.RUNNING, dto.slicing().status());
    }

    @Test
    void jobErrorsAreForTheAdminOnly() {
        SliceResult failed = new SliceResult();
        failed.status = SliceStatus.FAILED;
        failed.error = "The slicer can't be reached";

        assertNull(mapper.toDto(null, failed, USER).slicing().error());
        assertEquals("The slicer can't be reached", mapper.toDto(null, failed, ADMIN).slicing().error());
    }

    @Test
    void noFitCheckWithoutBuildPlate() {
        ModelInfoMapper.Context noPlate = new ModelInfoMapper.Context(Optional.empty(), false, false);

        ModelInfoDto dto = mapper.toDto(analyzed(plate(1, new Dimensions(999, 999, 999), null, null)), null, noPlate);

        assertNull(dto.fits());
        assertNull(dto.plates().getFirst().fits());
        assertFalse(dto.slicing().enabled());
    }

    @Test
    void onlyEmptyPlatesHaveNoTotals() {
        ModelInfoDto dto = mapper.toDto(analyzed(plate(1, null, null, null)), null, USER);

        assertNull(dto.totalGrams());
        assertNull(dto.weight());
        assertNull(dto.printSeconds());
    }
}
