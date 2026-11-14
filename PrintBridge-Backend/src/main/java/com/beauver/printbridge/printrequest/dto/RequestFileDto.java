package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.printrequest.ModelFileType;

import java.time.Instant;

/**
 * @param quantity  how many copies to print
 * @param modelInfo what the analyser read from the file
 */
public record RequestFileDto(
        Long id,
        String originalFilename,
        ModelFileType type,
        long sizeBytes,
        int quantity,
        Instant createdAt,
        ModelInfoDto modelInfo) {
}
