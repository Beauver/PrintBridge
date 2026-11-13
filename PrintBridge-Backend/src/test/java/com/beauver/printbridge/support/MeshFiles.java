package com.beauver.printbridge.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Reads the preview mesh format back in tests (see {@code MeshEncoder}). */
public final class MeshFiles {

    private MeshFiles() {
    }

    /** The JSON header and the buffer positioned at the first part's positions. */
    public record Decoded(JsonNode header, ByteBuffer body) {
    }

    public static Decoded decode(byte[] bytes, boolean gzipped) {
        try {
            byte[] raw = bytes;
            if (gzipped) {
                raw = new GZIPInputStream(new ByteArrayInputStream(bytes)).readAllBytes();
            }
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[4];
            buffer.get(magic);
            if (!new String(magic, StandardCharsets.US_ASCII).equals("PBM1")) {
                throw new IllegalArgumentException("Not a preview mesh");
            }
            int length = buffer.getInt();
            byte[] header = new byte[length];
            buffer.get(header);
            return new Decoded(new ObjectMapper().readTree(header), buffer.slice().order(ByteOrder.LITTLE_ENDIAN));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
