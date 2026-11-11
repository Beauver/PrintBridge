package com.beauver.printbridge.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code login} is a username or an email address. */
public record LoginRequest(
        @NotBlank(message = "Username or email is required") String login,
        @NotBlank(message = "Password is required") String password) {
}
