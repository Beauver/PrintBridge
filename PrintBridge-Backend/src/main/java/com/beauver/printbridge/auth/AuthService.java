package com.beauver.printbridge.auth;

import com.beauver.printbridge.auth.dto.AuthResponse;
import com.beauver.printbridge.common.error.InvalidCredentialsException;
import com.beauver.printbridge.user.PasswordHasher;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserMapper;
import com.beauver.printbridge.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid credentials";

    @Inject
    UserRepository users;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    AccessTokenIssuer accessTokens;

    @Inject
    RefreshTokenService refreshTokens;

    @Inject
    UserMapper mapper;

    @Transactional
    public AuthSession login(String login, String password) {
        User user = users.findByLogin(login.trim()).orElse(null);
        if (user == null) {
            passwordHasher.simulateVerification(password);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }
        if (!passwordHasher.matches(password, user.passwordHash)) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }
        return startSession(user);
    }

    /** Exchanges a refresh token for a new access token and a new refresh token. */
    @Transactional(dontRollbackOn = InvalidCredentialsException.class)
    public AuthSession refresh(String refreshToken) {
        return startSession(refreshTokens.consume(refreshToken));
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    /** Issues a fresh token pair for an already authenticated user. */
    @Transactional
    public AuthSession startSession(User user) {
        AuthResponse response = new AuthResponse(
                accessTokens.issue(user), accessTokens.expiresInSeconds(), mapper.toDto(user));
        return new AuthSession(response, refreshTokens.issue(user));
    }
}
