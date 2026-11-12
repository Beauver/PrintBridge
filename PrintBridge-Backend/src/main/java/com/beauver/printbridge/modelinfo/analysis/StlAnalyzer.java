package com.beauver.printbridge.modelinfo.analysis;

import com.beauver.printbridge.printrequest.ModelFileValidator;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Reads STL files (binary and ASCII). STL has no unit; PrintBridge assumes millimetres. */
final class StlAnalyzer {

    private static final int HEADER_SIZE = 84;
    private static final int TRIANGLE_SIZE = 50;
    /** Triangles read per chunk from a binary STL. */
    private static final int TRIANGLES_PER_CHUNK = 4096;

    ModelAnalysis analyze(Path file) throws IOException {
        MeshAccumulator mesh = new MeshAccumulator();
        if (ModelFileValidator.isBinaryStl(file)) {
            readBinary(file, mesh);
        } else {
            readAscii(file, mesh);
        }
        if (mesh.triangles() == 0) {
            throw new ModelAnalysisException("The model has no triangles");
        }
        PlateAnalysis plate = new PlateAnalysis(1, null, mesh.bounds().dimensions(), null, null, List.of());
        return new ModelAnalysis(ModelSource.MESH, mesh.volume(), List.of(plate));
    }

    /** 80-byte header, triangle count, then per triangle: normal, 3 vertices (floats) and 2 spare bytes. */
    private static void readBinary(Path file, MeshAccumulator mesh) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            in.skipNBytes(HEADER_SIZE);
            byte[] chunk = new byte[TRIANGLE_SIZE * TRIANGLES_PER_CHUNK];
            int read;
            while ((read = in.readNBytes(chunk, 0, chunk.length)) > 0) {
                ByteBuffer buffer = ByteBuffer.wrap(chunk, 0, read).order(ByteOrder.LITTLE_ENDIAN);
                for (int start = 0; start + TRIANGLE_SIZE <= read; start += TRIANGLE_SIZE) {
                    int v = start + 12;
                    mesh.addTriangle(
                            buffer.getFloat(v), buffer.getFloat(v + 4), buffer.getFloat(v + 8),
                            buffer.getFloat(v + 12), buffer.getFloat(v + 16), buffer.getFloat(v + 20),
                            buffer.getFloat(v + 24), buffer.getFloat(v + 28), buffer.getFloat(v + 32));
                }
            }
        }
    }

    /** Collects the {@code vertex x y z} lines three at a time; everything else is ignored. */
    private static void readAscii(Path file, MeshAccumulator mesh) throws IOException {
        double[] corners = new double[9];
        int corner = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.toLowerCase(Locale.ROOT).startsWith("vertex")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length != 4) {
                    throw new ModelAnalysisException("Invalid STL vertex line");
                }
                try {
                    for (int i = 0; i < 3; i++) {
                        corners[corner * 3 + i] = Double.parseDouble(parts[i + 1]);
                    }
                } catch (NumberFormatException e) {
                    throw new ModelAnalysisException("Invalid STL vertex line", e);
                }
                corner++;
                if (corner == 3) {
                    mesh.addTriangle(corners[0], corners[1], corners[2], corners[3], corners[4], corners[5],
                            corners[6], corners[7], corners[8]);
                    corner = 0;
                }
            }
        }
    }
}
