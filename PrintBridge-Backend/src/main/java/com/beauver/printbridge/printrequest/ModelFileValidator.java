package com.beauver.printbridge.printrequest;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Basic structural check that an upload really is the model type its extension claims. It is not
 * a full parser: it only rejects files that clearly are something else.
 */
@ApplicationScoped
public class ModelFileValidator {

    private static final int STL_HEADER_SIZE = 80;
    private static final int STL_BINARY_PREFIX = STL_HEADER_SIZE + 4;
    private static final int STL_TRIANGLE_SIZE = 50;
    /** How far into an ASCII STL to look for the first facet. */
    private static final int STL_ASCII_SCAN_LIMIT = 64 * 1024;

    public boolean isValid(Path file, ModelFileType type) {
        try {
            return switch (type) {
                case STL -> isBinaryStl(file) || isAsciiStl(file);
                case THREE_MF -> isThreeMf(file);
            };
        } catch (IOException e) {
            return false;
        }
    }

    /** Binary STL: 80-byte header, uint32 triangle count, then exactly 50 bytes per triangle. */
    public static boolean isBinaryStl(Path file) throws IOException {
        long size = Files.size(file);
        if (size < STL_BINARY_PREFIX + STL_TRIANGLE_SIZE) {
            return false;
        }
        byte[] prefix = new byte[STL_BINARY_PREFIX];
        try (InputStream in = Files.newInputStream(file)) {
            if (in.readNBytes(prefix, 0, prefix.length) != prefix.length) {
                return false;
            }
        }
        long triangles = Integer.toUnsignedLong(
                ByteBuffer.wrap(prefix, STL_HEADER_SIZE, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        return triangles > 0 && size == STL_BINARY_PREFIX + triangles * STL_TRIANGLE_SIZE;
    }

    /** ASCII STL: starts with "solid" and has a "facet" near the start. */
    static boolean isAsciiStl(Path file) throws IOException {
        byte[] start;
        try (InputStream in = Files.newInputStream(file)) {
            start = in.readNBytes(STL_ASCII_SCAN_LIMIT);
        }
        String text = new String(start, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return text.stripLeading().startsWith("solid") && text.contains("facet");
    }

    /** 3MF: a ZIP package with a model part under 3D/ (normally 3D/3dmodel.model). */
    static boolean isThreeMf(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return zip.stream()
                    .map(ZipEntry::getName)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(name -> name.startsWith("3d/") && name.endsWith(".model"));
        }
    }
}
