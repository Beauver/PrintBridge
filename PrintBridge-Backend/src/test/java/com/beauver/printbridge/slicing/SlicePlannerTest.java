package com.beauver.printbridge.slicing;

import com.beauver.printbridge.modelinfo.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.analysis.ProjectSettings;
import com.beauver.printbridge.printrequest.ModelFileType;
import com.beauver.printbridge.settings.BuildPlate;
import com.beauver.printbridge.settings.PrinterSettings;
import com.beauver.printbridge.settings.SlicingSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SlicePlannerTest {

    final SlicePlanner planner = new SlicePlanner();
    static final BigDecimal NOZZLE_02 = new BigDecimal("0.2");
    static final BigDecimal NOZZLE_04 = new BigDecimal("0.4");
    static final BigDecimal NOZZLE_06 = new BigDecimal("0.6");
    static final BigDecimal NOZZLE_08 = new BigDecimal("0.8");
    static final PrinterSettings P1S = new PrinterSettings(1, "Bambu Lab P1S", new BuildPlate(256, 256, 250),
            List.of(NOZZLE_02, NOZZLE_04), NOZZLE_04, "0.20mm Standard @P1S", "PLA Basic @P1S");
    static final PrinterSettings A1 = new PrinterSettings(2, "Bambu Lab A1", new BuildPlate(256, 256, 256),
            List.of(NOZZLE_04, NOZZLE_06), NOZZLE_06, "0.30mm Standard @A1", "PLA Basic @A1");
    static final SlicingSettings SETTINGS = new SlicingSettings(true, Duration.ofMinutes(10), List.of(P1S, A1), P1S);
    static final Map<String, String> KEPT = Map.of("wall_loops", "4");

    private static Optional<ProjectSettings> project(BigDecimal nozzle, String printer) {
        return Optional.of(new ProjectSettings(nozzle, printer, 256, 256, KEPT, 3));
    }

    @Test
    void stlUsesTheDefaultPrinter() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.STL, ModelSource.MESH, List.of(1), Optional.empty());

        assertEquals(new SlicePlan("stl", List.of(1), "Bambu Lab P1S", NOZZLE_04, false, "0.20mm Standard @P1S",
                "PLA Basic @P1S", 1, Map.of(), true, null, null), plan);
    }

    @Test
    void plainThreeMfIsTreatedLikeAnStl() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.MESH, List.of(1), Optional.empty());

        assertEquals("3mf", plan.format());
        assertEquals("0.20mm Standard @P1S", plan.process());
        assertTrue(plan.arrange());
    }

    @Test
    void ownedPrinterAndNozzleKeepTheWholeProject() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(1, 3),
                project(NOZZLE_02, "bambu lab p1s"));

        assertEquals(new SlicePlan("3mf", List.of(1, 3), "Bambu Lab P1S", NOZZLE_02, true, null, null, 1, Map.of(),
                false, null, null), plan);
    }

    @Test
    void projectForAnotherOwnedPrinterIsSlicedOnThatPrinter() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(1),
                project(NOZZLE_06, "Bambu Lab A1"));

        assertEquals("Bambu Lab A1", plan.printer());
        assertEquals(NOZZLE_06, plan.nozzle());
        assertTrue(plan.projectPrinter());
        assertFalse(plan.arrange());
    }

    @Test
    void nozzleNotOwnedOnThatPrinterUsesItsDefaultsButKeepsTheDesignersChoices() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(2),
                project(NOZZLE_02, "Bambu Lab A1"));

        assertEquals(new SlicePlan("3mf", List.of(2), "Bambu Lab A1", NOZZLE_06, false, "0.30mm Standard @A1",
                "PLA Basic @A1", 3, KEPT, false, NOZZLE_02, null), plan);
    }

    @Test
    void ownedPrinterWithoutKnownNozzleUsesItsDefaultNozzleWithTheProjectsSettings() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(1),
                project(null, "Bambu Lab A1"));

        assertEquals(new SlicePlan("3mf", List.of(1), "Bambu Lab A1", NOZZLE_06, false, null, null, 1, Map.of(),
                false, null, null), plan);
    }

    @Test
    void printerThatIsNotOwnedUsesTheDefaultPrinterAndSaysWhatItWasMadeFor() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(1),
                project(NOZZLE_08, "Bambu Lab X1 Carbon"));

        assertEquals(new SlicePlan("3mf", List.of(1), "Bambu Lab P1S", NOZZLE_04, false, "0.20mm Standard @P1S",
                "PLA Basic @P1S", 3, KEPT, true, NOZZLE_08, "Bambu Lab X1 Carbon"), plan);
    }

    @Test
    void sameNozzleOnAnotherPrinterIsNotMentioned() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(1),
                project(new BigDecimal("0.40"), null));

        assertNull(plan.madeForNozzle());
        assertNull(plan.madeForPrinter());
        assertEquals("Bambu Lab P1S", plan.printer());
        assertTrue(plan.arrange());
    }

    @Test
    void projectWithoutSettingsUsesTheDefaultPrinterWithTheFilesSettings() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(),
                Optional.empty());

        assertEquals(new SlicePlan("3mf", List.of(1), "Bambu Lab P1S", NOZZLE_04, false, null, null, 1, Map.of(),
                true, null, null), plan);
    }
}
