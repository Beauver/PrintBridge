package com.beauver.printbridge.auth;

import com.beauver.printbridge.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TokenValidity {

    @Inject
    UserRepository users;

    /** True when the user still exists and the token is not older than their last password change. */
    @ActivateRequestContext
    @Transactional
    public boolean isValid(long userId, long issuedAtEpochSeconds) {
        return users.findByIdOptional(userId)
                .map(u -> issuedAtEpochSeconds >= u.credentialsChangedAt.getEpochSecond())
                .orElse(false);
    }
}
