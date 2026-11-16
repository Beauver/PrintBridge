package com.beauver.printbridge.auth;

import com.beauver.printbridge.auth.token.AccessTokenIssuer;
import com.beauver.printbridge.auth.token.RefreshTokenService;
import com.beauver.printbridge.common.error.InvalidCredentialsException;
import com.beauver.printbridge.common.error.TooManyRequestsException;
import com.beauver.printbridge.common.ratelimit.MutableClock;
import com.beauver.printbridge.common.ratelimit.RateLimiter;
import com.beauver.printbridge.user.PasswordHasher;
import com.beauver.printbridge.user.Role;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserMapper;
import com.beauver.printbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository users;
    @Mock
    PasswordHasher passwordHasher;
    @Mock
    AccessTokenIssuer accessTokens;
    @Mock
    RefreshTokenService refreshTokens;

    static final String IP = "10.0.0.1";

    final MutableClock clock = new MutableClock();
    AuthService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new AuthService();
        service.users = users;
        service.passwordHasher = passwordHasher;
        service.accessTokens = accessTokens;
        service.refreshTokens = refreshTokens;
        service.mapper = new UserMapper();
        service.rateLimiter = new RateLimiter(clock);
        user = new User();
        user.id = 1L;
        user.username = "alice";
        user.passwordHash = "hash";
        user.role = Role.USER;
    }

    private void stubSession() {
        when(accessTokens.issue(user)).thenReturn("access");
        when(accessTokens.expiresInSeconds()).thenReturn(900L);
        when(refreshTokens.issue(user)).thenReturn("refresh");
    }

    @Test
    void loginWithCorrectPasswordStartsSession() {
        when(users.findByLogin("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("secret", "hash")).thenReturn(true);
        stubSession();

        AuthSession session = service.login(" alice ", "secret", IP);

        assertEquals("access", session.response().accessToken());
        assertEquals(900L, session.response().expiresIn());
        assertEquals("alice", session.response().user().username());
        assertEquals("refresh", session.refreshToken());
    }

    @Test
    void loginWithWrongPasswordFails() {
        when(users.findByLogin("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        var e = assertThrows(InvalidCredentialsException.class, () -> service.login("alice", "wrong", IP));
        assertEquals(AuthService.INVALID_CREDENTIALS, e.getMessage());
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void loginWithUnknownUserStillChecksAPassword() {
        when(users.findByLogin("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> service.login("ghost", "pw", IP));
        verify(passwordHasher).simulateVerification("pw");
    }

    private void failLogins(int times, String login, String address) {
        for (int i = 0; i < times; i++) {
            assertThrows(InvalidCredentialsException.class, () -> service.login(login, "wrong", address));
        }
    }

    @Test
    void tooManyFailedLoginsPauseTheAccountForThatAddress() {
        when(users.findByLogin("alice")).thenReturn(Optional.of(user));
        lenient().when(passwordHasher.matches("secret", "hash")).thenReturn(true);
        stubSession();
        failLogins(AuthService.MAX_FAILED_LOGINS, "alice", IP);
        clock.advance(Duration.ofMinutes(5));

        // Even the right password has to wait, and no password is checked.
        clearInvocations(passwordHasher);
        TooManyRequestsException e = assertThrows(TooManyRequestsException.class,
                () -> service.login("alice", "secret", IP));
        assertEquals(600, e.retryAfterSeconds());
        verifyNoInteractions(passwordHasher);

        // Another address is not affected, and the window ends by itself.
        assertNotNull(service.login("alice", "secret", "10.0.0.2"));
        clock.advance(Duration.ofMinutes(10));
        assertNotNull(service.login("alice", "secret", IP));
    }

    @Test
    void usernameAndEmailOfOneAccountAddUp() {
        when(users.findByLogin("alice")).thenReturn(Optional.of(user));
        when(users.findByLogin("alice@example.com")).thenReturn(Optional.of(user));
        failLogins(3, "alice", IP);
        failLogins(2, "alice@example.com", IP);

        assertThrows(TooManyRequestsException.class, () -> service.login("alice", "wrong", IP));
    }

    @Test
    void aSuccessfulLoginClearsTheFailedAttempts() {
        when(users.findByLogin("alice")).thenReturn(Optional.of(user));
        lenient().when(passwordHasher.matches("secret", "hash")).thenReturn(true);
        stubSession();
        failLogins(AuthService.MAX_FAILED_LOGINS - 1, "alice", IP);
        service.login("alice", "secret", IP);

        failLogins(AuthService.MAX_FAILED_LOGINS - 1, "alice", IP);
        assertNotNull(service.login("alice", "secret", IP));
    }

    @Test
    void unknownNamesAreLimitedLikeAccounts() {
        when(users.findByLogin(anyString())).thenReturn(Optional.empty());
        failLogins(AuthService.MAX_FAILED_LOGINS, " Ghost ", IP);

        assertThrows(TooManyRequestsException.class, () -> service.login("ghost", "pw", IP));
        // Another name from the same address has its own count.
        failLogins(1, "phantom", IP);
    }

    @Test
    void refreshConsumesTokenAndStartsNewSession() {
        when(refreshTokens.consume("old")).thenReturn(user);
        stubSession();

        assertEquals("refresh", service.refresh("old").refreshToken());
    }

    @Test
    void logoutRevokesToken() {
        service.logout("token");
        verify(refreshTokens).revoke("token");
    }
}
