package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/** Multipart form to add a model file to an existing request. */
public class NewFileForm {

    @RestForm("file")
    @NotNull(message = "A model file is required")
    public FileUpload file;

    /** Copies of this file; 1 when left out. */
    @RestForm
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100, message = "Quantity must be at most 100")
    public Integer quantity;
}
