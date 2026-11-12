package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.printrequest.ModelFileType;

import java.time.Instant;

public record RequestFileDto(Long id, String originalFilename, ModelFileType type, long sizeBytes, Instant createdAt) {
}
