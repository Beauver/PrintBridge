package com.beauver.printbridge.slicing;

import com.beauver.printbridge.modelfile.threemf.ProjectSettings;
import com.beauver.printbridge.settings.SlicingSettings;
import com.beauver.printbridge.settings.printer.BuildPlate;
import com.beauver.printbridge.settings.printer.PrinterSettings;
import com.beauver.printbridge.slicing.dto.ResliceChoiceDto;
import com.beauver.printbridge.slicing.result.SliceResult;
import com.beauver.printbridge.slicing.result.SliceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Where the "Re-slice with…" dialog starts, and when the project's own settings are possible (#21). */
class CustomResliceServiceTest {

    static final BigDecimal NOZZLE_02 = new BigDecimal("0.2");
    static final BigDecimal NOZZLE_04 = new BigDecimal("0.4");
    static final PrinterSettings P1S = new PrinterSettings(1, "Bambu Lab P1S", new BuildPlate(256, 256, 250),
            List.of(NOZZLE_02, NOZZLE_04), NOZZLE_04, "0.20mm Standard @P1S", "PLA Basic @P1S");
    static final PrinterSettings A1 = new PrinterSettings(2, "Bambu Lab A1", new BuildPlate(256, 256, 256),
            List.of(NOZZLE_04), NOZZLE_04, "0.20mm Standard @A1", "PLA Basic @A1");
    static final List<PrinterSettings> PRINTERS = List.of(P1S, A1);

    private static Optional<ProjectSettings> project(BigDecimal nozzle, String printer) {
        return Optional.of(new ProjectSettings(nozzle, printer, 256, 256, Map.of(), 2));
    }

    private static SliceResult done(String printer, BigDecimal nozzle) {
        SliceResult result = new SliceResult();
        result.status = SliceStatus.DONE;
        result.printer = printer;
        result.nozzle = nozzle;
        return result;
    }

    @Test
    void withoutAResultItStartsAtTheDefaultPrinterAndItsProfiles() {
        assertEquals(new ResliceChoiceDto(2L, NOZZLE_04, "0.20mm Standard @A1", "PLA Basic @A1", false),
                CustomResliceService.start(PRINTERS, A1, Optional.empty(), null));
    }

    @Test
    void itStartsWhereTheFileWasLastSliced() {
        assertEquals(new ResliceChoiceDto(1L, NOZZLE_02, "0.20mm Standard @P1S", "PLA Basic @P1S", false),
                CustomResliceService.start(PRINTERS, A1, Optional.empty(), done("Bambu Lab P1S", NOZZLE_02)));
    }

    @Test
    void aProjectStartsWithItsOwnSettingsWhereThatIsPossible() {
        assertEquals(new ResliceChoiceDto(1L, NOZZLE_02, null, null, false), CustomResliceService.start(PRINTERS, P1S,
                project(NOZZLE_02, "Bambu Lab P1S"), done("Bambu Lab P1S", NOZZLE_02)));
        assertEquals(new ResliceChoiceDto(1L, NOZZLE_04, "0.20mm Standard @P1S", null, false),
                CustomResliceService.start(PRINTERS, P1S, project(NOZZLE_02, "Bambu Lab P1S"), null));
    }

    @Test
    void aCustomChoiceIsKeptWhileItsPrinterAndNozzleAreOwned() {
        SliceResult custom = done("Bambu Lab A1", NOZZLE_04);
        custom.custom = true;
        custom.chosenPrinter = "Bambu Lab A1";
        custom.chosenNozzle = NOZZLE_04;
        custom.chosenProcess = "0.12mm Fine";
        assertEquals(new ResliceChoiceDto(2L, NOZZLE_04, "0.12mm Fine", null, true),
                CustomResliceService.start(PRINTERS, P1S, Optional.empty(), custom));

        custom.chosenNozzle = NOZZLE_02;
        assertFalse(CustomResliceService.start(PRINTERS, P1S, Optional.empty(), custom).custom());
    }

    @Test
    void aPrinterThatIsGoneFallsBackToTheDefaultOrTheFirstOrNothing() {
        assertEquals(1L, CustomResliceService.start(PRINTERS, P1S, Optional.empty(), done("Gone", NOZZLE_04))
                .printerId());
        assertEquals(2L, CustomResliceService.start(List.of(A1), P1S, Optional.empty(), null).printerId());
        assertEquals(new ResliceChoiceDto(null, null, null, null, false),
                CustomResliceService.start(List.of(), null, Optional.empty(), null));
    }

    @Test
    void theProjectsOwnSettingsOnlyOnItsOwnPrinterAndNozzle() {
        assertTrue(CustomResliceService.ownSettingsPossible(project(NOZZLE_02, " bambu lab p1s"), "Bambu Lab P1S",
                new BigDecimal("0.20")));
        assertFalse(CustomResliceService.ownSettingsPossible(project(NOZZLE_02, "Bambu Lab P1S"), "Bambu Lab P1S",
                NOZZLE_04));
        assertFalse(CustomResliceService.ownSettingsPossible(project(NOZZLE_02, "Bambu Lab P1S"), "Bambu Lab A1",
                NOZZLE_02));
        assertFalse(CustomResliceService.ownSettingsPossible(project(null, null), "Bambu Lab P1S", NOZZLE_02));
        assertFalse(CustomResliceService.ownSettingsPossible(Optional.empty(), "Bambu Lab P1S", NOZZLE_02));
    }

    @Test
    void aNozzleThatIsNoLongerOwnedFailsTheJob() {
        SlicingSettings settings = new SlicingSettings(true, Duration.ofMinutes(10), PRINTERS, P1S);
        assertNull(SlicingService.missingChoice(new CustomSlice("Bambu Lab A1", NOZZLE_04, null, null), settings));
        assertEquals("The chosen 0.2 mm nozzle isn't owned on Bambu Lab A1 any more",
                SlicingService.missingChoice(new CustomSlice("Bambu Lab A1", NOZZLE_02, null, null), settings));
    }
}
