package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.printrequest.ModelFileType;

import java.time.Instant;

/** @param modelInfo what the analyser read from the file */
public record RequestFileDto(
        Long id,
        String originalFilename,
        ModelFileType type,
        long sizeBytes,
        Instant createdAt,
        ModelInfoDto modelInfo) {
}
