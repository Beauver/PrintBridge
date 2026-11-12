package com.beauver.printbridge.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;

/**
 * Builds the httpOnly refresh-token cookie. It is only sent to the auth endpoints and never
 * readable from JavaScript; SameSite=Strict keeps other sites from triggering a refresh.
 */
@ApplicationScoped
public class RefreshCookie {

    public static final String NAME = "pb_refresh";
    static final String PATH = "/api/auth";

    @Inject
    AuthConfig config;

    public NewCookie create(String token) {
        return build(token, (int) config.refreshTokenTtl().toSeconds());
    }

    public NewCookie clear() {
        return build("", 0);
    }

    private NewCookie build(String value, int maxAge) {
        return new NewCookie.Builder(NAME)
                .value(value)
                .path(PATH)
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(config.cookieSecure())
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
    }
}
