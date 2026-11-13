package com.beauver.printbridge.modelinfo.analysis;

import com.beauver.printbridge.printrequest.ModelFileType;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.ThreeMfFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.beauver.printbridge.support.ThreeMfFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class PreviewMeshReaderTest {

    @TempDir
    Path dir;

    final PreviewMeshReader reader = new PreviewMeshReader();
    static final PreviewBed P1S = new PreviewBed(256, 256, 250);
    static final long LIMIT = 1000;

    private PreviewMesh read(String name, byte[] content, PreviewBed bed) throws ModelAnalysisException {
        ModelFileType type = name.endsWith(".stl") ? ModelFileType.STL : ModelFileType.THREE_MF;
        return reader.read(ModelFiles.write(dir, name, content), type, bed, LIMIT);
    }

    /** min x, min y, min z, max x, max y, max z over the given parts. */
    private static double[] bounds(List<PreviewPart> parts) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                -Double.MAX_VALUE};
        for (PreviewPart part : parts) {
            float[] p = part.positions();
            for (int i = 0; i + 2 < p.length; i += 3) {
                double[] point = {p[i], p[i + 1], p[i + 2]};
                for (int axis = 0; axis < 3; axis++) {
                    b[axis] = Math.min(b[axis], point[axis]);
                    b[axis + 3] = Math.max(b[axis + 3], point[axis]);
                }
            }
        }
        return b;
    }

    private static List<PreviewPart> onPlate(PreviewMesh mesh, int plate) {
        return mesh.parts().stream().filter(part -> part.plate() == plate).toList();
    }

    @Test
    void stlSharesCornersAndIsCentredOnTheBed() throws Exception {
        PreviewMesh mesh = read("cube.stl", ModelFiles.cubeStl(10), P1S);

        assertEquals(P1S, mesh.bed());
        assertEquals(List.of(new PreviewPlate(1, null)), mesh.plates());
        PreviewPart part = mesh.parts().getFirst();
        assertEquals(8, part.vertexCount());
        assertEquals(12, part.triangleCount());
        assertEquals(12, mesh.triangleCount());
        assertNull(part.color());
        assertArrayEquals(new double[]{123, 123, 0, 133, 133, 10}, bounds(mesh.parts()), 0.001);
    }

    @Test
    void withoutBedTheModelIsCentredOnTheOrigin() throws Exception {
        PreviewMesh mesh = read("cube.stl", ModelFiles.asciiStl(), null);

        assertNull(mesh.bed());
        assertArrayEquals(new double[]{-0.5, -0.5, 0, 0.5, 0.5, 0}, bounds(mesh.parts()), 0.001);
    }

    @Test
    void plainThreeMfIsOneCentredPlate() throws Exception {
        byte[] file = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10),
                        item(1, move(100, 100, 5)) + item(1, move(120, 100, 5))))
                .build();

        PreviewMesh mesh = read("plain.3mf", file, P1S);

        assertEquals(2, mesh.parts().size());
        assertArrayEquals(new double[]{113, 123, 0, 143, 133, 10}, bounds(mesh.parts()), 0.001);
    }

    @Test
    void bambuPlatesGoBackOntoTheirBedWithFilamentColours() throws Exception {
        PreviewMesh mesh = read("project.3mf", ThreeMfFiles.bambuProject().build(), null);

        assertEquals(List.of(new PreviewPlate(1, "First"), new PreviewPlate(2, null), new PreviewPlate(3, null)),
                mesh.plates());
        List<PreviewPart> first = onPlate(mesh, 1);
        // Both instances of object 2, without its modifier (the 50 mm box).
        assertEquals(2, first.size());
        assertEquals("#FFFFFF", first.getFirst().color());
        assertArrayEquals(new double[]{0, 0, 0, 40, 10, 10}, bounds(first), 0.001);
        // Plate 2 sits one stride (256 × 1.2) to the right in the project.
        List<PreviewPart> second = onPlate(mesh, 2);
        assertEquals("#FF0000", second.getFirst().color());
        assertArrayEquals(new double[]{-7.2, 0, 0, 12.8, 20, 20}, bounds(second), 0.001);
        assertTrue(onPlate(mesh, 3).isEmpty());
    }

    @Test
    void projectBedIsCentredOnTheTargetBed() throws Exception {
        PreviewMesh mesh = read("project.3mf", ThreeMfFiles.bambuProject().build(),
                new PreviewBed(180, 180, 180));

        // (180 - 256) / 2 = -38 on both axes.
        assertArrayEquals(new double[]{-38, -38, 0, 2, -28, 10}, bounds(onPlate(mesh, 1)), 0.001);
    }

    @Test
    void partsUseTheirOwnFilamentSlot() throws Exception {
        String modelSettings = """
                <config>
                  <object id="3">
                    <metadata key="extruder" value="2"/>
                    <part id="1" subtype="normal_part"><metadata key="extruder" value="1"/></part>
                    <part id="2" subtype="normal_part"/>
                  </object>
                  <plate><metadata key="plater_id" value="1"/>
                    <model_instance><metadata key="object_id" value="3"/></model_instance></plate>
                </config>
                """;
        byte[] file = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10) + cube(2, 5)
                        + components(3, new String[]{"1", null, null}, new String[]{"2", null, move(20, 0, 0)}),
                        item(3, null)))
                .entry("Metadata/model_settings.config", modelSettings)
                .entry("Metadata/project_settings.config", "{\"filament_colour\": [\"#111111\", \"#222222\"]}")
                .build();

        PreviewMesh mesh = read("parts.3mf", file, P1S);

        assertEquals(List.of("#111111", "#222222"), mesh.parts().stream().map(PreviewPart::color).toList());
        // Without a bed size in the project the plate is centred: 25 mm wide around 128.
        assertArrayEquals(new double[]{115.5, 123, 0, 140.5, 133, 10}, bounds(mesh.parts()), 0.001);
    }

    @Test
    void unknownFilamentSlotHasNoColour() throws Exception {
        byte[] file = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10), item(1, null)))
                .entry("Metadata/model_settings.config", "<config><object id=\"1\"/></config>")
                .build();

        PreviewMesh mesh = read("noplates.3mf", file, null);

        assertNull(mesh.parts().getFirst().color());
        assertEquals(List.of(new PreviewPlate(1, null)), mesh.plates());
    }

    @Test
    void refusesTooDetailedModels() {
        assertThrows(ModelTooDetailedException.class,
                () -> reader.read(ModelFiles.write(dir, "p.3mf", ThreeMfFiles.bambuProject().build()),
                        ModelFileType.THREE_MF, null, 30));
        assertThrows(ModelTooDetailedException.class,
                () -> reader.read(ModelFiles.write(dir, "c.stl", ModelFiles.cubeStl(1)), ModelFileType.STL, null,
                        11));
    }

    @Test
    void refusesFilesWithoutGeometry() {
        ModelAnalysisException empty = assertThrows(ModelAnalysisException.class,
                () -> read("empty.stl", ModelFiles.binaryStl(0), null));
        assertEquals("The model has no triangles", empty.getMessage());
        assertThrows(ModelAnalysisException.class, () -> read("empty.3mf", ModelFiles.threeMf(), null));
        assertThrows(ModelAnalysisException.class, () -> read("broken.3mf", new byte[]{1, 2, 3}, null));
        byte[] noObjects = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10), ""))
                .build();
        assertThrows(ModelAnalysisException.class, () -> read("nothing.3mf", noObjects, null));
        byte[] emptyPlates = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10), item(1, null)))
                .entry("Metadata/model_settings.config",
                        "<config><plate><metadata key=\"plater_id\" value=\"1\"/></plate></config>")
                .build();
        assertThrows(ModelAnalysisException.class, () -> read("emptyplates.3mf", emptyPlates, null));
    }

    @Test
    void readsPlateImages() throws Exception {
        Path project = ModelFiles.write(dir, "project.3mf", ThreeMfFiles.bambuProject()
                .entry("Metadata/plate_1.png", "png bytes").build());

        assertEquals("png bytes", new String(reader.thumbnail(project, ModelFileType.THREE_MF, 1).orElseThrow()));
        assertTrue(reader.thumbnail(project, ModelFileType.THREE_MF, 2).isEmpty());
        assertTrue(reader.thumbnail(project, ModelFileType.THREE_MF, 0).isEmpty());
        assertTrue(reader.thumbnail(project, ModelFileType.STL, 1).isEmpty());
        Path broken = ModelFiles.write(dir, "broken.3mf", new byte[]{1, 2, 3});
        assertThrows(ModelAnalysisException.class, () -> reader.thumbnail(broken, ModelFileType.THREE_MF, 1));
    }

    @Test
    void meshesNeedTheTriangles() throws Exception {
        Path file = ModelFiles.write(dir, "plain.3mf", ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10), item(1, null))).build());
        try (ThreeMfPackage pkg = ThreeMfPackage.open(file, new ReadBudget())) {
            ThreeMfGeometry geometry = ThreeMfGeometry.load(pkg);
            ThreeMfGeometry.BuildItem item = geometry.buildItems().getFirst();
            assertThrows(IllegalStateException.class,
                    () -> geometry.meshes(item, ThreeMfGeometry.ComponentFilter.ALL));
        }
        try (ThreeMfPackage pkg = ThreeMfPackage.open(file, new ReadBudget(ReadBudget.MAX_BYTES, 100, 5))) {
            ModelTooDetailedException e = assertThrows(ModelTooDetailedException.class,
                    () -> ThreeMfGeometry.loadWithTriangles(pkg));
            assertEquals("The model has more than 5 triangles", e.getMessage());
        }
    }
}
