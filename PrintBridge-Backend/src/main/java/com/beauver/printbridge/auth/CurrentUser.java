package com.beauver.printbridge.auth;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** The authenticated caller of the current request. */
@RequestScoped
public class CurrentUser {

    @Inject
    JsonWebToken jwt;

    public long id() {
        return Long.parseLong(jwt.getSubject());
    }
}
