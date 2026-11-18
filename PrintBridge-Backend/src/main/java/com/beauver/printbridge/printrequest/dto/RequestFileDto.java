package com.beauver.printbridge.printrequest.dto;

import com.beauver.printbridge.modelfile.ModelFileType;
import com.beauver.printbridge.modelinfo.dto.ModelInfoDto;
import com.beauver.printbridge.printrequest.file.RequestFileKind;
import java.time.Instant;

/**
 * An entry of a request: an uploaded file or a link (#59).
 *
 * @param originalFilename files only
 * @param type             files only
 * @param sizeBytes        files only
 * @param url              links only
 * @param linkName         links only, optional
 * @param quantity         how many copies to print
 * @param modelInfo        what the analyser read from the file; {@code null} for links
 */
public record RequestFileDto(
        Long id,
        RequestFileKind kind,
        String originalFilename,
        ModelFileType type,
        Long sizeBytes,
        String url,
        String linkName,
        int quantity,
        Instant createdAt,
        ModelInfoDto modelInfo) {
}
