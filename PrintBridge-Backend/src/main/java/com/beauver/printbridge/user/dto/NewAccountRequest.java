package com.beauver.printbridge.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Data for a new account: used by first-run setup (admin) and by the admin creating users. */
public record NewAccountRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 32, message = "Username must be 3 to 32 characters")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username may only contain letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        @Size(max = 254, message = "Email is too long")
        String email,

        @NotBlank(message = "Password is required")
        // bcrypt only uses the first 72 bytes
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String password) {
}
