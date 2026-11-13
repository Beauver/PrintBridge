package com.beauver.printbridge.preview;

import com.beauver.printbridge.modelinfo.analysis.PreviewMesh;
import com.beauver.printbridge.modelinfo.analysis.PreviewPart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes a {@link PreviewMesh} in the compact binary form the frontend viewer reads (#13),
 * gzipped. All numbers little-endian:
 * <pre>
 * "PBM1"                  magic, 4 bytes
 * uint32 headerLength     bytes of the header, a multiple of 4
 * header                  UTF-8 JSON, padded with spaces:
 *                         {"bed": {"x","y","z"} | null, "plates": [{"index","name"}],
 *                          "parts": [{"plate","color","vertices","triangles"}]}
 * per part, in order:     float32[vertices × 3] positions (mm), uint32[triangles × 3] indices
 * </pre>
 * Sizes stay multiples of 4, so the browser can read the arrays without copying.
 */
@ApplicationScoped
public class MeshEncoder {

    public static final String MEDIA_TYPE = "application/vnd.printbridge.mesh";
    static final byte[] MAGIC = "PBM1".getBytes(StandardCharsets.US_ASCII);

    @Inject
    ObjectMapper objectMapper;

    /** The gzipped bytes; to be sent with {@code Content-Encoding: gzip}. */
    public byte[] encode(PreviewMesh mesh) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = new GZIPOutputStream(bytes, 64 * 1024)) {
            byte[] header = header(mesh);
            out.write(MAGIC);
            out.write(littleEndian(4).putInt(header.length).array());
            out.write(header);
            for (PreviewPart part : mesh.parts()) {
                ByteBuffer positions = littleEndian(part.positions().length * 4);
                positions.asFloatBuffer().put(part.positions());
                out.write(positions.array());
                ByteBuffer indices = littleEndian(part.indices().length * 4);
                indices.asIntBuffer().put(part.indices());
                out.write(indices.array());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private byte[] header(PreviewMesh mesh) throws JsonProcessingException {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("bed", mesh.bed());
        header.put("plates", mesh.plates());
        List<Map<String, Object>> parts = mesh.parts().stream().map(part -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("plate", part.plate());
            entry.put("color", part.color());
            entry.put("vertices", part.vertexCount());
            entry.put("triangles", part.triangleCount());
            return entry;
        }).toList();
        header.put("parts", parts);
        StringBuilder json = new StringBuilder(objectMapper.writeValueAsString(header));
        while (json.toString().getBytes(StandardCharsets.UTF_8).length % 4 != 0) {
            json.append(' ');
        }
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static ByteBuffer littleEndian(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }
}
