package com.beauver.printbridge.storage;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stores uploaded files on disk below {@code printbridge.storage.path}. Files are grouped in one
 * directory per owner key (the request id) and named by a random UUID, so no user input ever
 * becomes part of a path.
 */
@ApplicationScoped
public class FileStorage {

    @Inject
    StorageConfig config;

    /**
     * Copies {@code source} into storage and returns its path relative to the storage root.
     */
    public String store(Path source, String directory, String extension) {
        String relative = directory + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store uploaded file", e);
        }
        return relative;
    }

    /** Absolute path of a stored file. */
    public Path resolve(String relativePath) {
        Path root = root();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Path escapes the storage root: " + relativePath);
        }
        return resolved;
    }

    /** Deletes one stored file. Like {@link #deleteDirectory}, a failure is logged, not thrown. */
    public void deleteFile(String relativePath) {
        Path file = resolve(relativePath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Log.warnf(e, "Could not delete stored file %s", file);
        }
    }

    /**
     * Deletes a directory and everything in it. Failures are logged, not thrown: the database is
     * the source of truth and a leftover file must not break the operation that removed it.
     */
    public void deleteDirectory(String directory) {
        Path dir = resolve(directory);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    Log.warnf(e, "Could not delete stored file %s", path);
                }
            });
        } catch (IOException e) {
            Log.warnf(e, "Could not delete storage directory %s", dir);
        }
    }

    private Path root() {
        return Path.of(config.path()).toAbsolutePath().normalize();
    }
}
