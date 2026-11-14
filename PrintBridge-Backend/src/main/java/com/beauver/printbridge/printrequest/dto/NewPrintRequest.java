package com.beauver.printbridge.printrequest.dto;

/** @param quantity copies of the first file */
public record NewPrintRequest(String name, String description, UploadedModel file, int quantity) {
}
