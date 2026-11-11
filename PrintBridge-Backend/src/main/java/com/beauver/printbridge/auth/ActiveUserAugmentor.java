package com.beauver.printbridge.auth;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Rejects access tokens of users that were deleted or whose password was reset after the token
 * was issued. Without this, such a token would keep working until it expires.
 */
@ApplicationScoped
public class ActiveUserAugmentor implements SecurityIdentityAugmentor {

    @Inject
    TokenValidity validity;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            return Uni.createFrom().item(identity);
        }
        return context.runBlocking(() -> {
            if (!validity.isValid(Long.parseLong(jwt.getSubject()), jwt.getIssuedAtTime())) {
                throw new AuthenticationFailedException("Token no longer valid");
            }
            return identity;
        });
    }
}
