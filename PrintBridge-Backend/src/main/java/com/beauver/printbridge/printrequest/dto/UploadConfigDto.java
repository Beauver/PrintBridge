package com.beauver.printbridge.printrequest.dto;

import java.util.List;

/** Upload limits, so the frontend can reject a file before uploading it. The backend checks again. */
public record UploadConfigDto(long maxSizeBytes, List<String> allowedExtensions) {
}
