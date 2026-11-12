package com.beauver.printbridge.modelinfo.analysis;

import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.ThreeMfFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static com.beauver.printbridge.support.ThreeMfFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectSettingsTest {

    @TempDir
    Path dir;

    private Optional<ProjectSettings> read(String projectSettings) {
        ThreeMfFiles file = ThreeMfFiles.builder().entry("3D/3dmodel.model", model("millimeter", cube(1, 1), item(1, null)));
        if (projectSettings != null) {
            file.entry("Metadata/project_settings.config", projectSettings);
        }
        return ProjectSettings.read(ModelFiles.write(dir, "project.3mf", file.build()));
    }

    @Test
    void readsWhatSlicingNeeds() {
        ProjectSettings settings = read("""
                {"nozzle_diameter": ["0.20"], "printer_model": "Bambu Lab P1S",
                 "printable_area": ["0x0", "256x0", "256x256", "0x256"],
                 "sparse_infill_density": "15%", "wall_loops": "4", "enable_support": ["1"],
                 "filament_type": ["PLA", "PLA", "PETG"], "layer_height": "0.12"}
                """).orElseThrow();

        assertEquals(new BigDecimal("0.2"), settings.nozzle());
        assertEquals("Bambu Lab P1S", settings.printerModel());
        assertEquals(256, settings.bedWidth());
        assertEquals(256, settings.bedDepth());
        assertEquals(Map.of("sparse_infill_density", "15%", "wall_loops", "4", "enable_support", "1"),
                settings.keptSettings());
        assertEquals(3, settings.filamentSlots());
    }

    @Test
    void toleratesMissingAndOddValues() {
        ProjectSettings settings = read("""
                {"nozzle_diameter": "x", "printable_area": ["0x0", "nope"], "wall_loops": {"a": 1}}
                """).orElseThrow();

        assertNull(settings.nozzle());
        assertNull(settings.printerModel());
        assertNull(settings.bedWidth());
        assertEquals(Map.of(), settings.keptSettings());
        assertEquals(1, settings.filamentSlots());
        assertNull(read("{\"nozzle_diameter\": [\"0\"], \"printable_area\": [\"0xa\"]}").orElseThrow().nozzle());
        assertNull(read("{\"printable_area\": []}").orElseThrow().bedDepth());
    }

    @Test
    void emptyWhenThereAreNoSettings() {
        assertTrue(read(null).isEmpty());
        assertTrue(read("").isEmpty());
        assertTrue(read("{broken").isEmpty());
        assertTrue(ProjectSettings.read(ModelFiles.write(dir, "x.3mf", new byte[]{1, 2, 3})).isEmpty());
    }
}
