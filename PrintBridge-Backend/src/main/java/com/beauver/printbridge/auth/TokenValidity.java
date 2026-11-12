package com.beauver.printbridge.auth;

import com.beauver.printbridge.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Database checks on an access token that the token itself can't answer.
 * <p>
 * Signature, issuer and expiry are verified by SmallRye JWT before any of our code runs; an
 * expired or forged token never gets here and is answered with 401.
 */
@ApplicationScoped
public class TokenValidity {

    @Inject
    UserRepository users;

    /** True when the user still exists and the token is not older than their last password change. */
    @Transactional
    public boolean isValid(long userId, long issuedAtEpochSeconds) {
        return users.findByIdOptional(userId)
                .map(u -> issuedAtEpochSeconds >= u.credentialsChangedAt.getEpochSecond())
                .orElse(false);
    }
}
