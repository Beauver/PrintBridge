package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/** Multipart form for a new print request. */
public class NewPrintRequestForm {

    @RestForm
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    public String name;

    @RestForm
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    public String description;

    @RestForm("file")
    @NotNull(message = "A model file is required")
    public FileUpload file;
}
