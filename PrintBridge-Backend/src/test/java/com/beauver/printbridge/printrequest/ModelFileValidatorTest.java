package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.support.ModelFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ModelFileValidatorTest {

    @TempDir
    Path dir;

    final ModelFileValidator validator = new ModelFileValidator();

    private boolean valid(byte[] content, ModelFileType type) {
        return validator.isValid(ModelFiles.write(dir, "model", content), type);
    }

    @Test
    void acceptsBinaryStl() {
        assertTrue(valid(ModelFiles.binaryStl(3), ModelFileType.STL));
    }

    @Test
    void acceptsBinaryStlWhoseHeaderStartsWithSolid() {
        byte[] stl = ModelFiles.binaryStl(2);
        System.arraycopy("solid".getBytes(StandardCharsets.US_ASCII), 0, stl, 0, 5);
        assertTrue(valid(stl, ModelFileType.STL));
    }

    @Test
    void acceptsAsciiStl() {
        assertTrue(valid(ModelFiles.asciiStl(), ModelFileType.STL));
    }

    @Test
    void rejectsBinaryStlWithWrongTriangleCount() {
        byte[] stl = Arrays.copyOf(ModelFiles.binaryStl(3), 84 + 50 * 3 + 7);
        assertFalse(valid(stl, ModelFileType.STL));
    }

    @Test
    void rejectsBinaryStlWithoutTriangles() {
        byte[] stl = Arrays.copyOf(ModelFiles.binaryStl(0), 134);
        assertFalse(valid(stl, ModelFileType.STL));
    }

    @Test
    void rejectsTextThatIsNotStl() {
        assertFalse(valid("solid but no triangles here".getBytes(StandardCharsets.US_ASCII), ModelFileType.STL));
        assertFalse(valid("hello facet".getBytes(StandardCharsets.US_ASCII), ModelFileType.STL));
    }

    @Test
    void rejectsTinyFile() {
        assertFalse(valid(new byte[]{1, 2, 3}, ModelFileType.STL));
    }

    @Test
    void acceptsThreeMf() {
        assertTrue(valid(ModelFiles.threeMf(), ModelFileType.THREE_MF));
    }

    @Test
    void rejectsZipWithoutModelPart() {
        assertFalse(valid(ModelFiles.zip("readme.txt"), ModelFileType.THREE_MF));
    }

    @Test
    void rejectsNonZipAsThreeMf() {
        assertFalse(valid(ModelFiles.asciiStl(), ModelFileType.THREE_MF));
    }

    @Test
    void missingFileIsInvalid() {
        assertFalse(validator.isValid(dir.resolve("nope"), ModelFileType.STL));
    }
}
