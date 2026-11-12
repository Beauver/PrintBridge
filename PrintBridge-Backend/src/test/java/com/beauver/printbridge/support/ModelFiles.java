package com.beauver.printbridge.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal valid and invalid model files for tests. */
public final class ModelFiles {

    private ModelFiles() {
    }

    public static byte[] binaryStl(int triangles) {
        ByteBuffer buffer = ByteBuffer.allocate(84 + 50 * triangles).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[80]);
        buffer.putInt(triangles);
        return buffer.array();
    }

    /** Binary STL of a closed cube from (0,0,0) to (size,size,size). */
    public static byte[] cubeStl(float size) {
        float[][] corners = {
                {0, 0, 0}, {size, 0, 0}, {size, size, 0}, {0, size, 0},
                {0, 0, size}, {size, 0, size}, {size, size, size}, {0, size, size}};
        int[][] triangles = {
                {0, 2, 1}, {0, 3, 2}, {4, 5, 6}, {4, 6, 7}, {0, 1, 5}, {0, 5, 4},
                {3, 7, 6}, {3, 6, 2}, {0, 4, 7}, {0, 7, 3}, {1, 2, 6}, {1, 6, 5}};
        ByteBuffer buffer = ByteBuffer.allocate(84 + 50 * triangles.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[80]);
        buffer.putInt(triangles.length);
        for (int[] triangle : triangles) {
            buffer.putFloat(0).putFloat(0).putFloat(0);
            for (int corner : triangle) {
                buffer.putFloat(corners[corner][0]).putFloat(corners[corner][1]).putFloat(corners[corner][2]);
            }
            buffer.putShort((short) 0);
        }
        return buffer.array();
    }

    public static byte[] asciiStl() {
        return """
                solid cube
                  facet normal 0 0 1
                    outer loop
                      vertex 0 0 0
                      vertex 1 0 0
                      vertex 0 1 0
                    endloop
                  endfacet
                endsolid cube
                """.getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] threeMf() {
        return zip("3D/3dmodel.model");
    }

    public static byte[] zip(String entryName) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("<model/>".getBytes(StandardCharsets.UTF_8));
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path write(Path dir, String name, byte[] content) {
        try {
            return Files.write(dir.resolve(name), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
