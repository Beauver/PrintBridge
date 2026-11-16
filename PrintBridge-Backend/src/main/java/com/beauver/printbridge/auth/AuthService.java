package com.beauver.printbridge.auth;

import com.beauver.printbridge.auth.dto.AuthResponse;
import com.beauver.printbridge.auth.token.AccessTokenIssuer;
import com.beauver.printbridge.auth.token.RefreshTokenService;
import com.beauver.printbridge.common.error.InvalidCredentialsException;
import com.beauver.printbridge.common.error.TooManyRequestsException;
import com.beauver.printbridge.common.ratelimit.RateLimiter;
import com.beauver.printbridge.user.PasswordHasher;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserMapper;
import com.beauver.printbridge.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class AuthService {

    static final String INVALID_CREDENTIALS = "Invalid credentials";
    static final int MAX_FAILED_LOGINS = 5;
    static final Duration FAILED_LOGIN_WINDOW = Duration.ofMinutes(15);

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

    @Inject
    RateLimiter rateLimiter;

    /**
     * Logs in with a username or email address.
     * <p>
     * Failed attempts are counted per account and client address: after
     * {@link #MAX_FAILED_LOGINS} within {@link #FAILED_LOGIN_WINDOW} that combination has to wait
     * for the window to end, even with the right password. Counting the address too means nobody
     * can lock someone else out by guessing at their username. Names that don't exist are counted
     * the same way, so the answers don't tell whether an account exists.
     *
     * @param clientAddress where the request comes from
     * @throws InvalidCredentialsException when the login or password is wrong
     * @throws TooManyRequestsException    when there were too many failed attempts
     */
    @Transactional
    public AuthSession login(String login, String password, String clientAddress) {
        User user = users.findByLogin(login.trim()).orElse(null);
        String failures = failuresKey(user, login, clientAddress);
        Optional<Duration> blocked = rateLimiter.blockedFor(failures, MAX_FAILED_LOGINS);
        if (blocked.isPresent()) {
            // Before the password check: not spending bcrypt time on these is the point.
            throw new TooManyRequestsException(blocked.get());
        }
        if (user == null) {
            passwordHasher.simulateVerification(password);
            rateLimiter.record(failures, FAILED_LOGIN_WINDOW);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }
        if (!passwordHasher.matches(password, user.passwordHash)) {
            rateLimiter.record(failures, FAILED_LOGIN_WINDOW);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }
        rateLimiter.reset(failures);
        return startSession(user);
    }

    /**
     * An account is counted by its id, so trying its username and its email address add up. A
     * name without an account is counted as typed.
     */
    private static String failuresKey(User user, String login, String clientAddress) {
        String account = "name:" + login.trim().toLowerCase(Locale.ROOT);
        if (user != null) {
            account = "id:" + user.id;
        }
        return "login-failures|" + account + "|" + clientAddress;
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
