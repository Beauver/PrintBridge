package com.beauver.printbridge.printrequest.dto;

import java.util.List;

/**
 * Upload limits, so the frontend can reject a file before uploading it. The backend checks again.
 *
 * @param maxSizeBytes largest model file
 * @param maxFiles     most files one request may hold
 */
public record UploadConfigDto(long maxSizeBytes, int maxFiles, List<String> allowedExtensions) {
}
