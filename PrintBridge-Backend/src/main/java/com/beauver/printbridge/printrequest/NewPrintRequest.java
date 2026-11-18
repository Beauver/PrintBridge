package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.printrequest.file.NewLink;
import com.beauver.printbridge.printrequest.file.UploadedModel;

/**
 * A new request with its first entry: exactly one of {@code file} and {@code link} is set (#59).
 *
 * @param quantity copies of the first file or link
 */
public record NewPrintRequest(
        String name,
        String description,
        UploadedModel file,
        NewLink link,
        int quantity) {
}
