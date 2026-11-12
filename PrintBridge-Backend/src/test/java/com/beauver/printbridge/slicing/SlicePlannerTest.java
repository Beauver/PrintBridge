package com.beauver.printbridge.slicing;

import com.beauver.printbridge.modelinfo.analysis.ModelSource;
import com.beauver.printbridge.modelinfo.analysis.ProjectSettings;
import com.beauver.printbridge.printrequest.ModelFileType;
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
    static final BigDecimal NOZZLE_08 = new BigDecimal("0.8");
    static final SlicingSettings SETTINGS = new SlicingSettings(true, Duration.ofMinutes(10), "Bambu Lab P1S",
            List.of(NOZZLE_02, NOZZLE_04), NOZZLE_04, "0.20mm Standard", "PLA Basic");
    static final Map<String, String> KEPT = Map.of("wall_loops", "4");

    private static Optional<ProjectSettings> project(BigDecimal nozzle, String printer) {
        return Optional.of(new ProjectSettings(nozzle, printer, 256, 256, KEPT, 3));
    }

    @Test
    void stlUsesTheDefaultNozzleAndProfiles() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.STL, ModelSource.MESH, List.of(1), Optional.empty());

        assertEquals(new SlicePlan("stl", List.of(1), "Bambu Lab P1S", NOZZLE_04, false, "0.20mm Standard",
                "PLA Basic", 1, Map.of(), true, null), plan);
    }

    @Test
    void plainThreeMfIsTreatedLikeAnStl() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.MESH, List.of(1), Optional.empty());

        assertEquals("3mf", plan.format());
        assertEquals("0.20mm Standard", plan.process());
        assertTrue(plan.arrange());
    }

    @Test
    void ownedNozzleOnTheSamePrinterKeepsTheWholeProject() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(1, 3),
                project(NOZZLE_02, "bambu lab p1s"));

        assertEquals(new SlicePlan("3mf", List.of(1, 3), "Bambu Lab P1S", NOZZLE_02, true, null, null, 1, Map.of(),
                false, null), plan);
    }

    @Test
    void ownedNozzleOnAnotherPrinterSwapsThePrinterAndRearranges() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(1),
                project(NOZZLE_04, "Bambu Lab X1 Carbon"));

        assertFalse(plan.projectPrinter());
        assertNull(plan.process());
        assertTrue(plan.arrange());
        assertNull(plan.madeForNozzle());
    }

    @Test
    void nozzleThatIsNotOwnedUsesTheDefaultsButKeepsTheDesignersChoices() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(2),
                project(NOZZLE_08, "Bambu Lab P1S"));

        assertEquals(new SlicePlan("3mf", List.of(2), "Bambu Lab P1S", NOZZLE_04, false, "0.20mm Standard",
                "PLA Basic", 3, KEPT, false, NOZZLE_08), plan);
    }

    @Test
    void unknownNozzleUsesTheDefaultWithTheProjectsSettings() {
        SlicePlan plan = planner.plan(SETTINGS, ModelFileType.THREE_MF, ModelSource.BAMBU, List.of(),
                Optional.empty());

        assertEquals(new SlicePlan("3mf", List.of(1), "Bambu Lab P1S", NOZZLE_04, false, null, null, 1, Map.of(),
                true, null), plan);
    }
}
