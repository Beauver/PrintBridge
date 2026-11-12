package com.beauver.printbridge.auth;

import com.beauver.printbridge.auth.dto.AuthResponse;

/** Result of a successful login/refresh/setup: the response body plus the refresh token for the cookie. */
public record AuthSession(AuthResponse response, String refreshToken) {
}
