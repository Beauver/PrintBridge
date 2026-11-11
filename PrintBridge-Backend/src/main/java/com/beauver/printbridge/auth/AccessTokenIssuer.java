package com.beauver.printbridge.auth;

import com.beauver.printbridge.user.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

/** Signs short-lived JWT access tokens. Subject = user id, groups = role. */
@ApplicationScoped
public class AccessTokenIssuer {

    @Inject
    AuthConfig config;

    public String issue(User user) {
        return Jwt.subject(String.valueOf(user.id))
                .upn(user.username)
                .groups(Set.of(user.role.name()))
                .expiresIn(config.accessTokenTtl())
                .sign();
    }

    public long expiresInSeconds() {
        return config.accessTokenTtl().toSeconds();
    }
}
