package com.beauver.printbridge.auth;

import com.beauver.printbridge.user.Role;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** The authenticated caller of the current request. */
@RequestScoped
public class CurrentUser {

    @Inject
    JsonWebToken jwt;

    public long id() {
        String subject = jwt.getSubject();
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("JWT subject is not a valid numeric user id: " + subject, e);
        }
    }

    public boolean isAdmin() {
        return jwt.getGroups().contains(Role.ADMIN_ROLE);
    }

    public Caller caller() {
        return new Caller(id(), isAdmin());
    }
}
