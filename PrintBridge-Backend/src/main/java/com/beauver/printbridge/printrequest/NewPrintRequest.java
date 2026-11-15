package com.beauver.printbridge.printrequest;

import com.beauver.printbridge.printrequest.file.UploadedModel;

/** @param quantity copies of the first file */
public record NewPrintRequest(String name, String description, UploadedModel file, int quantity) {
}
