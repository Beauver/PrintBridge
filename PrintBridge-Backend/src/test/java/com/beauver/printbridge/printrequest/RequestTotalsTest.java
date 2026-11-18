package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.modelinfo.ModelInfoStatus;
import com.beauver.printbridge.modelfile.ModelFileType;
import com.beauver.printbridge.modelfile.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.WeightKind;
import com.beauver.printbridge.printrequest.dto.RequestFileDto;
import com.beauver.printbridge.printrequest.dto.RequestTotalsDto;
import com.beauver.printbridge.printrequest.file.ItemStatus;
import com.beauver.printbridge.printrequest.file.RequestFileKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequestTotalsTest {

    private static RequestFileDto file(int quantity, Double grams, WeightKind weight, Long seconds, Boolean fits) {
        ModelInfoDto info = new ModelInfoDto(ModelInfoStatus.ANALYZED, ModelSource.MESH, 1.0, grams, weight, seconds,
                fits, null, List.of(), null);
        return new RequestFileDto(1L, RequestFileKind.FILE, "a.stl", ModelFileType.STL, 10L, null, null, quantity,
                ItemStatus.PENDING, null, List.of(), Instant.EPOCH, info);
    }

    private static RequestFileDto pending(int quantity) {
        return new RequestFileDto(2L, RequestFileKind.FILE, "b.stl", ModelFileType.STL, 10L, null, null, quantity,
                ItemStatus.PENDING, null, List.of(), Instant.EPOCH,
                ModelInfoDto.withStatus(ModelInfoStatus.PENDING, null));
    }

    private static RequestFileDto link(int quantity) {
        return new RequestFileDto(3L, RequestFileKind.LINK, null, null, null, "https://example.com/model", null,
                quantity, ItemStatus.PENDING, null, List.of(), Instant.EPOCH, null);
    }

    @Test
    void totalsMultiplyByQuantity() {
        RequestTotalsDto totals = RequestTotals.of(List.of(
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
        RequestTotalsDto totals = RequestTotals.of(List.of(
                file(1, 1.0, WeightKind.EXACT, 1L, true),
                file(1, 1.0, WeightKind.EXACT, 1L, null),
                file(1, 1.0, WeightKind.EXACT, 1L, false)));
        assertEquals(false, totals.fits());
    }

    @Test
    void mixedWeightsArePartlyEstimated() {
        assertEquals(WeightKind.PARTLY_ESTIMATED, RequestTotals.of(List.of(
                file(1, 1.0, WeightKind.EXACT, 1L, null),
                file(1, 1.0, WeightKind.ESTIMATED, null, null))).weight());
        assertEquals(WeightKind.PARTLY_ESTIMATED, RequestTotals.of(List.of(
                file(1, 1.0, WeightKind.PARTLY_ESTIMATED, null, null))).weight());
        assertEquals(WeightKind.ESTIMATED, RequestTotals.of(List.of(
                file(1, 1.0, WeightKind.ESTIMATED, null, null),
                file(1, 1.0, WeightKind.ESTIMATED, null, null))).weight());
    }

    @Test
    void unknownFilesDoNotCountAndMakeTheTimePartial() {
        RequestTotalsDto totals = RequestTotals.of(List.of(file(2, 4.0, WeightKind.EXACT, 60L, null),
                pending(5)));

        assertEquals(7, totals.copies());
        assertEquals(8.0, totals.grams());
        assertEquals(120L, totals.printSeconds());
        assertTrue(totals.printTimePartial());
        assertNull(totals.fits());
    }

    @Test
    void nothingKnownGivesEmptyTotals() {
        RequestTotalsDto totals = RequestTotals.of(List.of(pending(1)));
        assertNull(totals.grams());
        assertNull(totals.weight());
        assertNull(totals.printSeconds());
        assertFalse(totals.printTimePartial());
        assertNull(totals.fits());
    }

    @Test
    void linksCountAsCopiesAndMarkTheTotalsLikeAFailedPlate() {
        RequestTotalsDto totals = RequestTotals.of(List.of(file(2, 10.0, WeightKind.EXACT, 600L, true), link(3)));

        assertEquals(1, totals.files());
        assertEquals(1, totals.links());
        assertEquals(5, totals.copies());
        assertEquals(20.0, totals.grams());
        assertEquals(WeightKind.PARTLY_ESTIMATED, totals.weight());
        assertEquals(1200L, totals.printSeconds());
        assertTrue(totals.printTimePartial());
        assertEquals(true, totals.fits());
    }

    @Test
    void anEstimateStaysAnEstimateWithLinks() {
        RequestTotalsDto totals = RequestTotals.of(List.of(file(1, 10.0, WeightKind.ESTIMATED, null, null), link(1)));
        assertEquals(WeightKind.ESTIMATED, totals.weight());
    }

    @Test
    void onlyLinksGiveEmptyTotals() {
        RequestTotalsDto totals = RequestTotals.of(List.of(link(2), link(1)));

        assertEquals(0, totals.files());
        assertEquals(2, totals.links());
        assertEquals(3, totals.copies());
        assertNull(totals.grams());
        assertNull(totals.weight());
        assertNull(totals.printSeconds());
        assertFalse(totals.printTimePartial());
        assertNull(totals.fits());
    }

    // --- item statuses (#61) ---

    private static RequestFileDto with(RequestFileDto entry, ItemStatus status) {
        return new RequestFileDto(entry.id(), entry.kind(), entry.originalFilename(), entry.type(), entry.sizeBytes(),
                entry.url(), entry.linkName(), entry.quantity(), status, null, List.of(), entry.createdAt(),
                entry.modelInfo());
    }

    @Test
    void declinedItemsOnlyCountAsDeclined() {
        RequestTotalsDto totals = RequestTotals.of(List.of(
                with(file(2, 10.0, WeightKind.EXACT, 600L, true), ItemStatus.COMPLETED),
                with(file(3, 99.0, WeightKind.ESTIMATED, null, false), ItemStatus.DECLINED),
                with(link(4), ItemStatus.DECLINED)));

        assertEquals(2, totals.files());
        assertEquals(1, totals.links());
        assertEquals(2, totals.declined());
        assertEquals(1, totals.completed());
        assertEquals(2, totals.copies());
        assertEquals(20.0, totals.grams());
        assertEquals(WeightKind.EXACT, totals.weight());
        assertEquals(1200L, totals.printSeconds());
        assertFalse(totals.printTimePartial());
        assertEquals(true, totals.fits());
    }
}
