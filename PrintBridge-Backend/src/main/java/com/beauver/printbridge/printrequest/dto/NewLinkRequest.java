package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A link to add to an existing request (#59).
 *
 * @param name     optional; shown instead of the URL's host
 * @param quantity copies; 1 when left out
 */
public record NewLinkRequest(
        @NotBlank(message = "A link is required")
        @Size(max = LinkFields.MAX_URL_LENGTH, message = "The link must be at most 2048 characters")
        @Pattern(regexp = LinkFields.URL_PATTERN, message = LinkFields.URL_MESSAGE)
        String url,
        @Size(max = LinkFields.MAX_NAME_LENGTH, message = "The link name must be at most 100 characters")
        String name,
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 100, message = "Quantity must be at most 100")
        Integer quantity) {
}
