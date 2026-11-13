package com.beauver.printbridge.modelinfo.analysis;

import com.beauver.printbridge.printrequest.ModelFileType;
import com.beauver.printbridge.support.ModelFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StlAnalyzerTest {

    @TempDir
    Path dir;

    final ModelAnalyzer analyzer = new ModelAnalyzer();

    private ModelAnalysis analyze(byte[] content) throws ModelAnalysisException {
        return analyzer.analyze(ModelFiles.write(dir, "model.stl", content), ModelFileType.STL);
    }

    @Test
    void readsSizeAndVolumeOfBinaryStl() throws Exception {
        ModelAnalysis analysis = analyze(ModelFiles.cubeStl(10));

        assertEquals(ModelSource.MESH, analysis.source());
        assertEquals(1000, analysis.volumeMm3(), 0.001);
        assertEquals(1, analysis.plates().size());
        PlateAnalysis plate = analysis.plates().getFirst();
        assertEquals(new Dimensions(10, 10, 10), plate.size());
        assertNull(plate.grams());
        assertNull(plate.printSeconds());
        assertTrue(plate.filaments().isEmpty());
    }

    @Test
    void readsAsciiStl() throws Exception {
        String stl = """
                solid cube
                  facet normal 0 0 1
                    outer loop
                      vertex 0 0 0
                      vertex 20 0 0
                      VERTEX 0 5.5 0
                    endloop
                  endfacet
                endsolid cube
                """;

        ModelAnalysis analysis = analyze(stl.getBytes(StandardCharsets.US_ASCII));

        assertEquals(new Dimensions(20, 5.5, 0), analysis.plates().getFirst().size());
        assertEquals(0, analysis.volumeMm3(), 0.001);
    }

    @Test
    void rejectsStlWithoutTriangles() {
        byte[] stl = "solid empty\n facet normal 0 0 1\n endfacet\nendsolid empty\n".getBytes(StandardCharsets.US_ASCII);

        ModelAnalysisException e = assertThrows(ModelAnalysisException.class, () -> analyze(stl));
        assertEquals("The model has no triangles", e.getMessage());
    }

    @Test
    void rejectsBrokenAsciiVertex() {
        byte[] wrongCount = "solid x\n facet\n vertex 1 2\n endfacet\nendsolid x\n".getBytes(StandardCharsets.US_ASCII);
        byte[] notANumber = "solid x\n facet\n vertex 1 2 abc\n endfacet\nendsolid x\n".getBytes(StandardCharsets.US_ASCII);

        assertThrows(ModelAnalysisException.class, () -> analyze(wrongCount));
        assertThrows(ModelAnalysisException.class, () -> analyze(notANumber));
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        byte[] stl = ModelFiles.binaryStl(1);
        ByteBuffer.wrap(stl).order(ByteOrder.LITTLE_ENDIAN).putFloat(84 + 12, Float.NaN);

        ModelAnalysisException e = assertThrows(ModelAnalysisException.class, () -> analyze(stl));
        assertEquals("The model contains invalid coordinates", e.getMessage());
    }

    @Test
    void reportsUnreadableFile() {
        Path missing = dir.resolve("missing.stl");

        assertThrows(ModelAnalysisException.class, () -> analyzer.analyze(missing, ModelFileType.STL));
    }
}
