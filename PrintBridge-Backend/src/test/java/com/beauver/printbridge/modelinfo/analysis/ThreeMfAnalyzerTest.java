package com.beauver.printbridge.modelinfo.analysis;

import com.beauver.printbridge.printrequest.ModelFileType;
import com.beauver.printbridge.support.ModelFiles;
import com.beauver.printbridge.support.ThreeMfFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static com.beauver.printbridge.support.ThreeMfFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class ThreeMfAnalyzerTest {

    @TempDir
    Path dir;

    final ModelAnalyzer analyzer = new ModelAnalyzer();

    private ModelAnalysis analyze(byte[] content) throws ModelAnalysisException {
        return analyze(content, new ReadBudget());
    }

    private ModelAnalysis analyze(byte[] content, ReadBudget budget) throws ModelAnalysisException {
        return analyzer.analyze(ModelFiles.write(dir, "model.3mf", content), ModelFileType.THREE_MF, budget);
    }

    private static byte[] plain(String unit, String resources, String build) {
        return ThreeMfFiles.builder().entry("3D/3dmodel.model", model(unit, resources, build)).build();
    }

    // --- plain 3MF ---

    @Test
    void readsPlainThreeMfAsOnePlate() throws Exception {
        ModelAnalysis analysis = analyze(plain("millimeter", cube(1, 10) + cube(2, 5),
                item(1, null) + item(2, move(20, 0, 0))));

        assertEquals(ModelSource.MESH, analysis.source());
        assertEquals(1125, analysis.volumeMm3(), 0.001);
        assertEquals(List.of(new PlateAnalysis(1, null, new Dimensions(25, 10, 10), 1125, null, null, List.of())),
                analysis.plates());
    }

    @Test
    void convertsUnitsToMillimetres() throws Exception {
        assertEquals(new Dimensions(10, 10, 10),
                analyze(plain("centimeter", cube(1, 1), item(1, null))).plates().getFirst().size());
        assertEquals(new Dimensions(25.4, 25.4, 25.4),
                analyze(plain("inch", cube(1, 1), item(1, null))).plates().getFirst().size());
        assertEquals(new Dimensions(1, 1, 1),
                analyze(plain("micron", cube(1, 1000), item(1, null))).plates().getFirst().size());
        assertEquals(new Dimensions(1000, 1000, 1000),
                analyze(plain("meter", cube(1, 1), item(1, null))).plates().getFirst().size());
        assertEquals(new Dimensions(304.8, 304.8, 304.8),
                analyze(plain("foot", cube(1, 1), item(1, null))).plates().getFirst().size());
    }

    @Test
    void appliesScaleAndMirrorTransforms() throws Exception {
        ModelAnalysis scaled = analyze(plain("millimeter", cube(1, 10), item(1, "2 0 0 0 2 0 0 0 2 0 0 0")));
        ModelAnalysis mirrored = analyze(plain("millimeter", cube(1, 10), item(1, "-1 0 0 0 1 0 0 0 1 0 0 0")));

        assertEquals(new Dimensions(20, 20, 20), scaled.plates().getFirst().size());
        assertEquals(8000, scaled.volumeMm3(), 0.001);
        assertEquals(1000, mirrored.volumeMm3(), 0.001);
    }

    @Test
    void followsComponentsInTheSamePart() throws Exception {
        String resources = cube(1, 10) + components(2, new String[]{"1", null, move(0, 0, 5)},
                new String[]{"1", null, move(10, 0, 0)});

        ModelAnalysis analysis = analyze(plain("millimeter", resources, item(2, null)));

        assertEquals(new Dimensions(20, 10, 15), analysis.plates().getFirst().size());
        assertEquals(2000, analysis.volumeMm3(), 0.001);
    }

    @Test
    void findsRootModelThroughRelationships() throws Exception {
        String rels = "<Relationships><Relationship Target=\"/3D/main.model\" Id=\"rel0\" "
                + "Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\"/></Relationships>";
        byte[] file = ThreeMfFiles.builder()
                .entry("_rels/.rels", rels)
                .entry("3D/main.model", model("millimeter", cube(1, 3), item(1, null)))
                .build();

        assertEquals(new Dimensions(3, 3, 3), analyze(file).plates().getFirst().size());
    }

    @Test
    void fallsBackToAnyModelPartUnderThreeD() throws Exception {
        byte[] file = ThreeMfFiles.builder()
                .entry("_rels/.rels", "<Relationships/>")
                .entry("3D/other.model", model("millimeter", cube(1, 4), item(1, null)))
                .build();

        assertEquals(new Dimensions(4, 4, 4), analyze(file).plates().getFirst().size());
    }

    @Test
    void rejectsBrokenPackages() {
        assertFailure("The file is not a valid 3MF package", "not a zip".getBytes(StandardCharsets.UTF_8));
        assertFailure("The 3MF has no model part", ThreeMfFiles.builder().build());
        assertFailure("Invalid 3MF model XML", ThreeMfFiles.builder().entry("3D/3dmodel.model", "<model><").build());
        assertFailure("Invalid 3MF relationships", ThreeMfFiles.builder().entry("_rels/.rels", "<x>")
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 1), item(1, null))).build());
    }

    @Test
    void rejectsInvalidModels() {
        assertFailure("The 3MF contains no geometry", plain("millimeter", cube(1, 1), ""));
        assertFailure("The 3MF refers to a missing object 7", plain("millimeter", cube(1, 1), item(7, null)));
        assertFailure("Unknown 3MF unit: parsec", plain("parsec", cube(1, 1), item(1, null)));
        assertFailure("Invalid transform: 1 0 0", plain("millimeter", cube(1, 1), item(1, "1 0 0")));
        assertFailure("Invalid transform: 1 0 0 0 1 0 0 0 1 0 0 x", plain("millimeter", cube(1, 1),
                item(1, "1 0 0 0 1 0 0 0 1 0 0 x")));
        assertFailure("Invalid transform: 1 0 0 0 1 0 0 0 1 0 0 NaN", plain("millimeter", cube(1, 1),
                item(1, "1 0 0 0 1 0 0 0 1 0 0 NaN")));
        assertFailure("A triangle refers to a missing vertex", plain("millimeter",
                "<object id=\"1\"><mesh><vertices><vertex x=\"0\" y=\"0\" z=\"0\"/></vertices>"
                        + "<triangles><triangle v1=\"0\" v2=\"1\" v3=\"2\"/></triangles></mesh></object>",
                item(1, null)));
        assertFailure("Invalid number in attribute x", plain("millimeter",
                "<object id=\"1\"><mesh><vertices><vertex x=\"a\" y=\"0\" z=\"0\"/></vertices></mesh></object>",
                item(1, null)));
        assertFailure("Invalid number in attribute x", plain("millimeter",
                "<object id=\"1\"><mesh><vertices><vertex x=\"Infinity\" y=\"0\" z=\"0\"/></vertices></mesh></object>",
                item(1, null)));
        assertFailure("Invalid number in attribute objectid", plain("millimeter", cube(1, 1),
                "<item objectid=\"one\"/>"));
        assertFailure("Missing part in 3MF: /3D/Objects/missing.model", plain("millimeter",
                components(2, new String[]{"1", "/3D/Objects/missing.model", null}), item(2, null)));
    }

    @Test
    void rejectsComponentCycles() {
        byte[] file = plain("millimeter", components(1, new String[]{"1", null, null}), item(1, null));

        assertFailure("Components are nested too deeply", file);
    }

    @Test
    void stopsAtTheByteLimit() {
        byte[] file = plain("millimeter", cube(1, 10), item(1, null));

        ModelAnalysisException e = assertThrows(ModelAnalysisException.class,
                () -> analyze(file, new ReadBudget(100, ReadBudget.MAX_VERTICES)));
        assertEquals("The file unpacks to more than 100 bytes", e.getMessage());
    }

    @Test
    void stopsAtTheVertexLimit() {
        byte[] file = plain("millimeter", cube(1, 10), item(1, null));

        ModelAnalysisException e = assertThrows(ModelAnalysisException.class,
                () -> analyze(file, new ReadBudget(ReadBudget.MAX_BYTES, 7)));
        assertEquals("The model has more than 7 vertices", e.getMessage());
    }

    private void assertFailure(String message, byte[] file) {
        ModelAnalysisException e = assertThrows(ModelAnalysisException.class, () -> analyze(file));
        assertEquals(message, e.getMessage());
    }

    // --- Bambu Studio / OrcaSlicer ---

    /**
     * Like Bambu Studio: root objects 2 and 4 are components pointing into 3D/Objects/object_1.model.
     * Object 2 has two instances and a modifier part (object 5, a big box that must not count).
     * Plate 1 holds both instances of object 2, plate 2 object 4, plate 3 is empty.
     */
    private static ThreeMfFiles bambuProject() {
        String root = model("millimeter",
                components(2, new String[]{"1", "/3D/Objects/object_1.model", null},
                        new String[]{"5", "/3D/Objects/object_1.model", null})
                        + components(4, new String[]{"3", "/3D/Objects/object_1.model", null}),
                item(2, move(0, 0, 0)) + item(2, move(30, 0, 0)) + item(4, move(300, 0, 0)));
        String objects = model("millimeter", cube(1, 10) + cube(3, 20) + box(5, 50, 50, 50), "");
        String modelSettings = """
                <?xml version="1.0" encoding="UTF-8"?>
                <config>
                  <object id="2">
                    <metadata key="name" value="Body"/>
                    <metadata key="extruder" value="1"/>
                    <part id="1" subtype="normal_part"><metadata key="name" value="Body"/></part>
                    <part id="5" subtype="modifier_part"><metadata key="extruder" value="3"/></part>
                  </object>
                  <object id="4">
                    <metadata key="extruder" value="2"/>
                    <part id="3" subtype="normal_part"/>
                  </object>
                  <plate>
                    <metadata key="plater_id" value="1"/>
                    <metadata key="plater_name" value="  First  "/>
                    <model_instance>
                      <metadata key="object_id" value="2"/>
                      <metadata key="instance_id" value="0"/>
                    </model_instance>
                    <model_instance>
                      <metadata key="object_id" value="2"/>
                      <metadata key="instance_id" value="1"/>
                    </model_instance>
                  </plate>
                  <plate>
                    <metadata key="plater_id" value="2"/>
                    <metadata key="plater_name" value=""/>
                    <model_instance>
                      <metadata key="object_id" value="4"/>
                      <metadata key="instance_id" value="9"/>
                    </model_instance>
                    <model_instance>
                      <metadata key="object_id" value="99"/>
                    </model_instance>
                    <model_instance>
                      <metadata key="instance_id" value="0"/>
                    </model_instance>
                  </plate>
                  <plate>
                    <metadata key="plater_id" value="3"/>
                  </plate>
                </config>
                """;
        String projectSettings = """
                {"filament_type": ["PLA", "PETG", "PLA"], "filament_colour": ["#FFFFFF", "#ff0000aa", "nope"],
                 "printable_area": ["0x0", "256x0", "256x256", "0x256"]}
                """;
        return ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", root)
                .entry("3D/Objects/object_1.model", objects)
                .entry("Metadata/model_settings.config", modelSettings)
                .entry("Metadata/project_settings.config", projectSettings)
                .entry("Metadata/slice_info.config", "<config><header/></config>");
    }

    @Test
    void readsUnslicedBambuProjectPlateByPlate() throws Exception {
        ModelAnalysis analysis = analyze(bambuProject().build());

        assertEquals(ModelSource.BAMBU, analysis.source());
        assertEquals(2 * 1000 + 8000, analysis.volumeMm3(), 0.001);
        assertEquals(List.of(
                new PlateAnalysis(1, "First", new Dimensions(40, 10, 10), 2000, null, null,
                        List.of(new FilamentUsage("PLA", "#FFFFFF", null))),
                new PlateAnalysis(2, null, new Dimensions(20, 20, 20), 8000, null, null,
                        List.of(new FilamentUsage("PETG", "#FF0000", null))),
                new PlateAnalysis(3, null, null, 0, null, null, List.of())), analysis.plates());
    }

    @Test
    void usesSlicedNumbersWhereThePlateWasSliced() throws Exception {
        String sliceInfo = """
                <config>
                  <header><header_item key="X-BBL-Client-Version" value="01.10.02.73"/></header>
                  <plate>
                    <metadata key="index" value="1"/>
                    <metadata key="prediction" value="2963"/>
                    <metadata key="weight" value="33.69"/>
                    <object identify_id="93" name="Body" skipped="false"/>
                    <filament id="1" type="PLA" color="#FFFFFF" used_m="10.61" used_g="33.69"/>
                    <filament id="2" type="PETG" color="#00ff00" used_m="1" used_g="x"/>
                    <warning msg="bed_temperature_too_high_than_filament" level="1"/>
                  </plate>
                  <plate>
                    <metadata key="index" value="nope"/>
                  </plate>
                </config>
                """;

        ModelAnalysis analysis = analyze(bambuProject().entry("Metadata/slice_info.config", sliceInfo).build());

        PlateAnalysis first = analysis.plates().get(0);
        assertEquals(33.69, first.grams());
        assertEquals(2963L, first.printSeconds());
        assertEquals(List.of(new FilamentUsage("PLA", "#FFFFFF", 33.69), new FilamentUsage("PETG", "#00FF00", null)),
                first.filaments());
        assertNull(analysis.plates().get(1).grams());
        assertNull(analysis.plates().get(1).printSeconds());
    }

    @Test
    void listsSameLookingFilamentsOnce() throws Exception {
        String projectSettings = """
                {"filament_type": ["PLA", "PETG", "PLA"], "filament_colour": ["#FFFFFF", "#FF0000", "#ffffff"]}
                """;
        String modelSettings = """
                <config>
                  <object id="1"><metadata key="extruder" value="1"/>
                    <part id="1"><metadata key="extruder" value="3"/></part></object>
                  <plate><metadata key="plater_id" value="1"/>
                    <model_instance><metadata key="object_id" value="1"/></model_instance></plate>
                </config>
                """;
        byte[] file = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 1), item(1, null)))
                .entry("Metadata/model_settings.config", modelSettings)
                .entry("Metadata/project_settings.config", projectSettings)
                .build();

        assertEquals(List.of(new FilamentUsage("PLA", "#FFFFFF", null)),
                analyze(file).plates().getFirst().filaments());
    }

    @Test
    void treatsBambuProjectWithoutPlatesAsOnePlate() throws Exception {
        byte[] file = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10), item(1, null)))
                .entry("Metadata/model_settings.config", "<config><object id=\"1\"/></config>")
                .build();

        ModelAnalysis analysis = analyze(file);

        assertEquals(ModelSource.BAMBU, analysis.source());
        assertEquals(new Dimensions(10, 10, 10), analysis.plates().getFirst().size());
        assertTrue(analysis.plates().getFirst().filaments().isEmpty());
    }

    @Test
    void rejectsBambuProjectWithOnlyEmptyPlates() {
        byte[] file = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10), item(1, null)))
                .entry("Metadata/model_settings.config",
                        "<config><plate><metadata key=\"plater_id\" value=\"1\"/></plate></config>")
                .build();

        assertFailure("The project has no objects on its plates", file);
    }

    @Test
    void rejectsBrokenBambuMetadata() {
        String root = model("millimeter", cube(1, 1), item(1, null));
        assertFailure("Invalid Metadata/model_settings.config", ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", root).entry("Metadata/model_settings.config", "<config>").build());
        assertFailure("Invalid Metadata/project_settings.config", ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", root).entry("Metadata/model_settings.config", "<config/>")
                .entry("Metadata/project_settings.config", "{nope").build());
        assertFailure("Invalid Metadata/slice_info.config", ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", root).entry("Metadata/model_settings.config", "<config/>")
                .entry("Metadata/slice_info.config", "<config>").build());
    }

    @Test
    void handlesEmptyProjectSettings() throws Exception {
        byte[] file = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 1), item(1, null)))
                .entry("Metadata/model_settings.config", "<config/>")
                .entry("Metadata/project_settings.config", "")
                .build();

        assertTrue(analyze(file).plates().getFirst().filaments().isEmpty());
    }

    // --- other slicers ---

    @Test
    void readsOtherSlicerProjectsAsPlainThreeMf() throws Exception {
        byte[] file = ThreeMfFiles.builder()
                .entry("3D/3dmodel.model", model("millimeter", cube(1, 10), item(1, null)))
                .entry("Metadata/Slic3r_PE.config", "; filament_type = PLA\n")
                .build();

        ModelAnalysis analysis = analyze(file);

        assertEquals(ModelSource.MESH, analysis.source());
        assertTrue(analysis.plates().getFirst().filaments().isEmpty());
    }
}
