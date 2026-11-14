package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.modelinfo.ModelInfoStatus;
import com.beauver.printbridge.modelinfo.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.WeightKind;
import com.beauver.printbridge.printrequest.dto.RequestFileDto;
import com.beauver.printbridge.printrequest.dto.RequestTotalsDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrintRequestMapperTest {

    private static RequestFileDto file(int quantity, Double grams, WeightKind weight, Long seconds, Boolean fits) {
        ModelInfoDto info = new ModelInfoDto(ModelInfoStatus.ANALYZED, ModelSource.MESH, 1.0, grams, weight, seconds,
                fits, null, List.of(), null);
        return new RequestFileDto(1L, "a.stl", ModelFileType.STL, 10, quantity, Instant.EPOCH, info);
    }

    private static RequestFileDto pending(int quantity) {
        return new RequestFileDto(2L, "b.stl", ModelFileType.STL, 10, quantity, Instant.EPOCH,
                ModelInfoDto.withStatus(ModelInfoStatus.PENDING, null));
    }

    @Test
    void totalsMultiplyByQuantity() {
        RequestTotalsDto totals = PrintRequestMapper.totals(List.of(
                file(2, 10.25, WeightKind.EXACT, 600L, true),
                file(3, 5.0, WeightKind.EXACT, 100L, true)));

        assertEquals(2, totals.files());
        assertEquals(5, totals.copies());
        assertEquals(35.5, totals.grams());
        assertEquals(WeightKind.EXACT, totals.weight());
        assertEquals(1500L, totals.printSeconds());
        assertFalse(totals.printTimePartial());
        assertEquals(true, totals.fits());
    }

    @Test
    void anyFileThatDoesNotFitMakesTheRequestNotFit() {
        RequestTotalsDto totals = PrintRequestMapper.totals(List.of(
                file(1, 1.0, WeightKind.EXACT, 1L, true),
                file(1, 1.0, WeightKind.EXACT, 1L, null),
                file(1, 1.0, WeightKind.EXACT, 1L, false)));
        assertEquals(false, totals.fits());
    }

    @Test
    void mixedWeightsArePartlyEstimated() {
        assertEquals(WeightKind.PARTLY_ESTIMATED, PrintRequestMapper.totals(List.of(
                file(1, 1.0, WeightKind.EXACT, 1L, null),
                file(1, 1.0, WeightKind.ESTIMATED, null, null))).weight());
        assertEquals(WeightKind.PARTLY_ESTIMATED, PrintRequestMapper.totals(List.of(
                file(1, 1.0, WeightKind.PARTLY_ESTIMATED, null, null))).weight());
        assertEquals(WeightKind.ESTIMATED, PrintRequestMapper.totals(List.of(
                file(1, 1.0, WeightKind.ESTIMATED, null, null),
                file(1, 1.0, WeightKind.ESTIMATED, null, null))).weight());
    }

    @Test
    void unknownFilesDoNotCountAndMakeTheTimePartial() {
        RequestTotalsDto totals = PrintRequestMapper.totals(List.of(file(2, 4.0, WeightKind.EXACT, 60L, null),
                pending(5)));

        assertEquals(7, totals.copies());
        assertEquals(8.0, totals.grams());
        assertEquals(120L, totals.printSeconds());
        assertTrue(totals.printTimePartial());
        assertNull(totals.fits());
    }

    @Test
    void nothingKnownGivesEmptyTotals() {
        RequestTotalsDto totals = PrintRequestMapper.totals(List.of(pending(1)));
        assertNull(totals.grams());
        assertNull(totals.weight());
        assertNull(totals.printSeconds());
        assertFalse(totals.printTimePartial());
        assertNull(totals.fits());
    }
}
