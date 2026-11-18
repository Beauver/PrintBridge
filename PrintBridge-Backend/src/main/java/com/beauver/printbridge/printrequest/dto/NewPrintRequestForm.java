package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Multipart form for a new print request. Its first entry is either a model file or a link
 * (#59), never both; the resource checks that. The others follow one by one.
 */
public class NewPrintRequestForm {

    @RestForm
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    public String name;

    @RestForm
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    public String description;

    @RestForm("file")
    public FileUpload file;

    /** A link instead of a file. */
    @RestForm
    @Size(max = LinkFields.MAX_URL_LENGTH, message = "The link must be at most 2048 characters")
    @Pattern(regexp = LinkFields.URL_PATTERN, message = LinkFields.URL_MESSAGE)
    public String url;

    /** Optional name of the link. */
    @RestForm
    @Size(max = LinkFields.MAX_NAME_LENGTH, message = "The link name must be at most 100 characters")
    public String linkName;

    /** Copies of the file or link; 1 when left out. */
    @RestForm
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100, message = "Quantity must be at most 100")
    public Integer quantity;
}
