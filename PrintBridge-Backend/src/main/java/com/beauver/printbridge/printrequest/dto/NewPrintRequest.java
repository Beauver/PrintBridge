package com.beauver.printbridge.printrequest.dto;

public record NewPrintRequest(String name, String description, UploadedModel file) {
}
