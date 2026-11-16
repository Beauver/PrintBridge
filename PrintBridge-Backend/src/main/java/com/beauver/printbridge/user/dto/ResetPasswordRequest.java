package com.beauver.printbridge.user.dto;

import com.beauver.printbridge.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        // bcrypt only uses the first 72 bytes, so a longer password would be cut off silently
        @MaxUtf8Bytes(value = 72, message = "Password is too long")
        String password) {
}
