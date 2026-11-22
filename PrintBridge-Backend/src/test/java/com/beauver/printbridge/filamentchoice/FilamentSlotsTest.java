package com.beauver.printbridge.filamentchoice;

import com.beauver.printbridge.modelfile.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.ModelInfoStatus;
import com.beauver.printbridge.modelinfo.WeightKind;
import com.beauver.printbridge.modelinfo.dto.FilamentDto;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.modelinfo.dto.PlateDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FilamentSlotsTest {

    private static PlateDto plate(int index, Double grams, FilamentDto... filaments) {
        return new PlateDto(index, null, 10.0, 10.0, 10.0, grams, false, null, null, List.of(filaments), null);
    }

    private static ModelInfoDto analysed(Double total, PlateDto... plates) {
        return new ModelInfoDto(ModelInfoStatus.ANALYZED, ModelSource.BAMBU, 1.0, total, WeightKind.EXACT, null, null,
                null, List.of(plates), null);
    }

    @Test
    void oneSlotPerColourOfEveryPlate() {
        ModelInfoDto info = analysed(60.0,
                plate(1, 40.0, new FilamentDto("PLA", "#FFFFFF", 30.0), new FilamentDto("PLA", "#000000", null)),
                plate(2, 20.0, new FilamentDto("PETG", "#FF0000", null)),
                plate(3, null));

        assertEquals(List.of(
                new FilamentSlot(1, "PLA", "#FFFFFF", 30.0),
                // Several colours without their own grams: the plate's can't be split.
                new FilamentSlot(1, "PLA", "#000000", null),
                // The plate's only colour gets the plate's grams.
                new FilamentSlot(2, "PETG", "#FF0000", 20.0)), FilamentSlots.of(info));
    }

    @Test
    void wholeItemWithoutColours() {
        assertEquals(List.of(FilamentSlot.wholeItem(12.5)), FilamentSlots.of(analysed(12.5, plate(1, 12.5))));
        assertEquals(List.of(FilamentSlot.wholeItem(null)), FilamentSlots.of(null));
        assertEquals(List.of(FilamentSlot.wholeItem(null)),
                FilamentSlots.of(ModelInfoDto.withStatus(ModelInfoStatus.FAILED, null)));
    }

    @Test
    void matchesColoursIgnoringCase() {
        FilamentSlot slot = new FilamentSlot(1, "PLA", "#ffffff", null);
        assertTrue(slot.matches(1, "PLA", "#FFFFFF"));
        assertFalse(slot.matches(2, "PLA", "#FFFFFF"));
        assertFalse(slot.matches(1, "PETG", "#FFFFFF"));
        assertFalse(slot.matches(1, "PLA", null));
        assertTrue(new FilamentSlot(1, null, null, null).matches(1, null, null));
        assertTrue(FilamentSlot.wholeItem(1.0).isWholeItem());
    }

    private static FilamentChoice choice(long fileId, Integer plate, String color, long filamentId) {
        FilamentChoice choice = new FilamentChoice();
        choice.fileId = fileId;
        choice.plateIndex = plate;
        choice.originalColor = color;
        if (color != null) {
            choice.originalType = "PLA";
        }
        choice.spoolmanFilamentId = filamentId;
        return choice;
    }


    @Test
    void gramsNeededAddUpEveryUseOfAFilamentTimesTheQuantity() {
        Map<Long, ChoiceItem> files = Map.of(
                1L, new ChoiceItem(1, 2, false),
                2L, new ChoiceItem(2, 3, false),
                3L, new ChoiceItem(3, 5, true),
                4L, new ChoiceItem(4, 1, false));
        Map<Long, List<FilamentSlot>> slots = Map.of(
                1L, List.of(new FilamentSlot(1, "PLA", "#FFFFFF", 10.0), new FilamentSlot(2, "PLA", "#000000", null)),
                2L, List.of(FilamentSlot.wholeItem(4.0)),
                3L, List.of(FilamentSlot.wholeItem(100.0)),
                4L, List.of(FilamentSlot.wholeItem(null)));
        List<FilamentChoice> choices = List.of(
                choice(1, 1, "#FFFFFF", 7),
                choice(1, 2, "#000000", 7),
                choice(2, null, null, 7),
                // Declined items aren't printed.
                choice(3, null, null, 7),
                // No longer in the file.
                choice(1, 1, "#123456", 7),
                // Nothing known about the grams.
                choice(4, null, null, 8));

        assertEquals(Map.of(7L, 32.0), FilamentChoiceService.gramsNeeded(choices, files, slots));
    }
}
