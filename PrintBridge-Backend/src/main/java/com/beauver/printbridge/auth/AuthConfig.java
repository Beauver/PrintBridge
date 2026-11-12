package com.beauver.printbridge.auth;

import io.smallrye.config.ConfigMapping;

import java.time.Duration;

@ConfigMapping(prefix = "printbridge.auth")
public interface AuthConfig {

    /** Lifetime of the JWT access token. */
    Duration accessTokenTtl();

    /** Lifetime of a refresh token; each use replaces it with a new one. */
    Duration refreshTokenTtl();

    /** Whether the refresh cookie gets the Secure flag (requires HTTPS outside localhost). */
    boolean cookieSecure();
}
