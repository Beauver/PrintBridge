package com.beauver.printbridge.user.dto;

import com.beauver.printbridge.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A user updating their own account. The current password is always required, so an unlocked
 * session alone can't take the account over. The password only changes when
 * {@code changePassword} is true; {@code newPassword} is ignored otherwise.
 */
public record UpdateAccountRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 32, message = "Username must be 3 to 32 characters")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username may only contain letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        @Size(max = 254, message = "Email is too long")
        String email,

        boolean changePassword,

        @Size(min = 8, message = "Password must be at least 8 characters")
        // bcrypt only uses the first 72 bytes, so a longer password would be cut off silently
        @MaxUtf8Bytes(value = 72, message = "Password is too long")
        String newPassword,

        @NotBlank(message = "Current password is required")
        String currentPassword) {

    @AssertTrue(message = "Password is required")
    public boolean isNewPasswordGivenWhenChanging() {
        return !changePassword || (newPassword != null && !newPassword.isBlank());
    }
}
