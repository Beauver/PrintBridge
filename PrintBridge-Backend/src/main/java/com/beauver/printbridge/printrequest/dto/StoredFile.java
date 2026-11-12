package com.beauver.printbridge.printrequest.dto;

import java.nio.file.Path;

/** A file ready to be streamed to the client. */
public record StoredFile(
        Path path,
        String filename,
        String contentType,
        long size) {
}
