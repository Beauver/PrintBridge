package com.beauver.printbridge.auth;

import com.beauver.printbridge.common.error.InvalidCredentialsException;
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

        AuthSession session = service.login(" alice ", "secret");

        assertEquals("access", session.response().accessToken());
        assertEquals(900L, session.response().expiresIn());
        assertEquals("alice", session.response().user().username());
        assertEquals("refresh", session.refreshToken());
    }

    @Test
    void loginWithWrongPasswordFails() {
        when(users.findByLogin("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        var e = assertThrows(InvalidCredentialsException.class, () -> service.login("alice", "wrong"));
        assertEquals(AuthService.INVALID_CREDENTIALS, e.getMessage());
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void loginWithUnknownUserStillChecksAPassword() {
        when(users.findByLogin("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> service.login("ghost", "pw"));
        verify(passwordHasher).simulateVerification("pw");
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
