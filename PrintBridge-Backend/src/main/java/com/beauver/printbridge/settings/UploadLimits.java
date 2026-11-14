package com.beauver.printbridge.settings;

/**
 * The upload limits that apply right now (#23): the admin's settings, capped by the server's
 * ceiling.
 *
 * @param maxFiles      most files one request may hold
 * @param maxFileSizeMb largest model file in MB
 * @param ceilingMb     the server's ceiling per file ({@code UPLOAD_MAX_SIZE_MB}); only raised through the environment
 */
public record UploadLimits(int maxFiles, int maxFileSizeMb, int ceilingMb) {

    public static final int DEFAULT_MAX_FILES = 10;
    public static final int MAX_FILES = 50;

    private static final long BYTES_PER_MB = 1024L * 1024L;

    public long maxFileSizeBytes() {
        return maxFileSizeMb * BYTES_PER_MB;
    }
}
