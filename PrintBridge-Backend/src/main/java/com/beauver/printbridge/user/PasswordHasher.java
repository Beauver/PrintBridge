package com.beauver.printbridge.user;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

/** Bcrypt hashing, wrapped in a bean so services can be unit-tested without real hashing. */
@ApplicationScoped
public class PasswordHasher {

    // Compared against when the login name is unknown, so a failed login takes as long
    // whether or not the account exists (no user enumeration via timing).
    private final String dummyHash = BcryptUtil.bcryptHash("timing-equaliser");

    public String hash(String password) {
        return BcryptUtil.bcryptHash(password);
    }

    public boolean matches(String password, String hash) {
        return BcryptUtil.matches(password, hash);
    }

    /** Burns the same time as a real check; always false. */
    public boolean matchesNothing(String password) {
        BcryptUtil.matches(password, dummyHash);
        return false;
    }
}
