package com.beauver.printbridge.printrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * New name and description of a request (#84); the same rules as when creating it.
 *
 * @param description optional; empty or blank clears it
 */
public record RequestDetailsChange(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,
        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description) {
}
