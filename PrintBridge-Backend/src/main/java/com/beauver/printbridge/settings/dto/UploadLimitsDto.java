package com.beauver.printbridge.settings.dto;

/**
 * The upload limits that apply now.
 *
 * @param ceilingMb the server's ceiling per file ({@code UPLOAD_MAX_SIZE_MB}); the size setting can't go higher
 */
public record UploadLimitsDto(int maxFiles, int maxFileSizeMb, int ceilingMb) {
}
