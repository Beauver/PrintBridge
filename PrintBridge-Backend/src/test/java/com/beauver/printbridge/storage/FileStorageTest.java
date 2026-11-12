package com.beauver.printbridge.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageTest {

    @TempDir
    Path root;

    @TempDir
    Path uploads;

    FileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorage();
        storage.config = () -> root.toString();
    }

    @Test
    void storesCopyUnderDirectoryWithRandomName() throws IOException {
        Path source = Files.writeString(uploads.resolve("model.stl"), "data");

        String relative = storage.store(source, "12", "stl");

        assertTrue(relative.matches("12/[0-9a-f-]{36}\\.stl"), relative);
        assertEquals("data", Files.readString(storage.resolve(relative)));
        assertTrue(Files.exists(source), "source is copied, not moved");
    }

    @Test
    void storeFailureIsUnchecked() {
        assertThrows(UncheckedIOException.class, () -> storage.store(uploads.resolve("missing"), "1", "stl"));
    }

    @Test
    void resolveRejectsPathsOutsideRoot() {
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../outside"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("."));
    }

    @Test
    void deleteDirectoryRemovesEverything() throws IOException {
        Path source = Files.writeString(uploads.resolve("a.stl"), "data");
        storage.store(source, "7", "stl");
        storage.store(source, "7", "stl");

        storage.deleteDirectory("7");

        assertFalse(Files.exists(root.resolve("7")));
    }

    @Test
    void deleteMissingDirectoryIsNoop() {
        assertDoesNotThrow(() -> storage.deleteDirectory("404"));
    }
}
