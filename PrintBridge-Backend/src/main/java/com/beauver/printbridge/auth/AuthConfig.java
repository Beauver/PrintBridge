package com.beauver.printbridge.auth;

import io.smallrye.config.ConfigMapping;

import java.time.Duration;

@ConfigMapping(prefix = "printbridge.auth")
public interface AuthConfig {

    /** Lifetime of the JWT access token. */
    Duration accessTokenTtl();

    /** Lifetime of a refresh token; each use replaces it with a new one. */
    Duration refreshTokenTtl();

    /**
     * How long a refresh token that was just replaced may still be presented. Browser tabs that
     * load together all send the same cookie; without this only the first one would succeed.
     */
    Duration refreshGrace();

    /** Whether the refresh cookie gets the Secure flag (requires HTTPS outside localhost). */
    boolean cookieSecure();
}
