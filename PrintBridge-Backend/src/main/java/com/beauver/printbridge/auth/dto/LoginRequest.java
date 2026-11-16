package com.beauver.printbridge.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code login} is a username or an email address. */
public record LoginRequest(
        @NotBlank(message = "Username or email is required")
        // No username or email is longer; also keeps what the failed-login counter remembers small.
        @Size(max = 254, message = "Username or email is too long")
        String login,
        @NotBlank(message = "Password is required") String password) {
}
