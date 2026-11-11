package com.beauver.printbridge.support;

import com.beauver.printbridge.auth.AuthService;
import com.beauver.printbridge.user.Role;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserService;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Database helpers for @QuarkusTest tests. */
@ApplicationScoped
public class TestData {

    public static final String PASSWORD = "password123";

    @Inject
    EntityManager em;

    @Inject
    UserService userService;

    @Inject
    AuthService authService;

    @Transactional
    public void reset() {
        em.createNativeQuery("TRUNCATE users, refresh_tokens RESTART IDENTITY CASCADE").executeUpdate();
    }

    public User admin() {
        return userService.create(new NewAccountRequest("admin", "admin@example.com", PASSWORD), Role.ADMIN);
    }

    public User user(String username) {
        return userService.create(new NewAccountRequest(username, username + "@example.com", PASSWORD), Role.USER);
    }

    public String accessToken(User user) {
        return authService.startSession(user).response().accessToken();
    }
}
