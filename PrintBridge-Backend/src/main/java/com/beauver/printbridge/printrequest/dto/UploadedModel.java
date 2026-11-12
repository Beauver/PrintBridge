package com.beauver.printbridge.printrequest.dto;

import java.nio.file.Path;

/** An uploaded file as received by the resource: the client's filename and a temporary file. */
public record UploadedModel(String filename, Path path, long size) {
}
