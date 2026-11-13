package com.beauver.printbridge.preview;

import com.beauver.printbridge.modelinfo.analysis.PreviewBed;
import com.beauver.printbridge.modelinfo.analysis.PreviewMesh;
import com.beauver.printbridge.modelinfo.analysis.PreviewPart;
import com.beauver.printbridge.modelinfo.analysis.PreviewPlate;
import com.beauver.printbridge.support.MeshFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class MeshEncoderTest {

    final MeshEncoder encoder = new MeshEncoder();

    MeshEncoderTest() {
        encoder.objectMapper = new ObjectMapper();
    }

    @Test
    void writesHeaderAndArraysInOrder() {
        PreviewMesh mesh = new PreviewMesh(new PreviewBed(256, 256, 250),
                List.of(new PreviewPlate(1, "Lid"), new PreviewPlate(2, null)),
                List.of(new PreviewPart(1, "#FF0000", new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}, new int[]{0, 1, 2}),
                        new PreviewPart(2, null, new float[]{5, 5, 5, 6, 5, 5, 5, 6, 5}, new int[]{2, 1, 0})));

        MeshFiles.Decoded decoded = MeshFiles.decode(encoder.encode(mesh), true);

        assertEquals("{\"bed\":{\"x\":256,\"y\":256,\"z\":250},\"plates\":[{\"index\":1,\"name\":\"Lid\"},"
                        + "{\"index\":2,\"name\":null}],\"parts\":[{\"plate\":1,\"color\":\"#FF0000\","
                        + "\"vertices\":3,\"triangles\":1},{\"plate\":2,\"color\":null,\"vertices\":3,"
                        + "\"triangles\":1}]}",
                decoded.header().toString());
        ByteBuffer body = decoded.body();
        assertEquals(1f, body.getFloat(12));
        assertEquals(2, body.getInt(36 + 8));
        assertEquals(5f, body.getFloat(48));
        assertEquals(0, body.getInt(84 + 8));
        assertEquals(96, body.remaining());
    }

    @Test
    void padsTheHeaderToFourBytes() throws Exception {
        PreviewMesh mesh = new PreviewMesh(null, List.of(new PreviewPlate(1, "é")), List.of());

        byte[] gzipped = encoder.encode(mesh);
        byte[] raw = new GZIPInputStream(new ByteArrayInputStream(gzipped)).readAllBytes();
        MeshFiles.Decoded decoded = MeshFiles.decode(gzipped, true);

        assertEquals(0, ByteBuffer.wrap(raw, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() % 4);
        assertTrue(decoded.header().path("bed").isNull());
        assertEquals("é", decoded.header().path("plates").get(0).path("name").asText());
        assertEquals(0, decoded.body().remaining());
    }
}
