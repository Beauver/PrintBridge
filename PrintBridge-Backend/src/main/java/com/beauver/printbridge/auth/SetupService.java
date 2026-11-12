package com.beauver.printbridge.auth;

import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.user.Role;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserRepository;
import com.beauver.printbridge.user.UserService;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

/** First-run setup: creating the single admin account. Locked once an admin exists. */
@ApplicationScoped
public class SetupService {

    static final String ALREADY_DONE = "Setup has already been completed";

    @Inject
    UserRepository users;

    @Inject
    UserService userService;

    @Inject
    AuthService authService;

    public boolean isSetupRequired() {
        return !users.adminExists();
    }

    /**
     * Creates the admin and logs them in. A unique index on the admin role guarantees only one
     * concurrent setup can win; the loser gets the same 409.
     */
    @Transactional
    public AuthSession createAdmin(NewAccountRequest request) {
        if (users.adminExists()) {
            throw new ConflictException(ALREADY_DONE);
        }
        User admin = userService.create(request, Role.ADMIN);
        try {
            users.flush();
        } catch (PersistenceException e) {
            // Another setup request created the admin between our check and our insert.
            throw new ConflictException(ALREADY_DONE);
        }
        return authService.startSession(admin);
    }
}
