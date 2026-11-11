package com.beauver.printbridge.auth;

import com.beauver.printbridge.common.error.InvalidCredentialsException;
import com.beauver.printbridge.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    RefreshTokenRepository tokens;

    @Mock
    AuthConfig config;

    RefreshTokenService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService();
        service.tokens = tokens;
        service.config = config;
        service.clock = Clock.fixed(NOW, ZoneOffset.UTC);
        user = new User();
        user.id = 7L;
    }

    private RefreshToken stored(Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken();
        token.user = user;
        token.expiresAt = expiresAt;
        token.revokedAt = revokedAt;
        return token;
    }

    @Test
    void issueStoresOnlyTheHash() {
        when(config.refreshTokenTtl()).thenReturn(Duration.ofDays(14));
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).persist(captor.capture());
        RefreshToken saved = captor.getValue();
        assertEquals(RefreshTokenService.hash(raw), saved.tokenHash);
        assertNotEquals(raw, saved.tokenHash);
        assertEquals(NOW.plus(Duration.ofDays(14)), saved.expiresAt);
        assertSame(user, saved.user);
    }

    @Test
    void issueCreatesDifferentTokens() {
        when(config.refreshTokenTtl()).thenReturn(Duration.ofDays(14));
        assertNotEquals(service.issue(user), service.issue(user));
    }

    @Test
    void consumeRevokesAndReturnsUser() {
        RefreshToken token = stored(NOW.plusSeconds(60), null);
        when(tokens.findByHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));

        assertSame(user, service.consume("raw"));
        assertEquals(NOW, token.revokedAt);
    }

    @Test
    void consumeRejectsMissingToken() {
        assertThrows(InvalidCredentialsException.class, () -> service.consume(null));
        assertThrows(InvalidCredentialsException.class, () -> service.consume(" "));
        verifyNoInteractions(tokens);
    }

    @Test
    void consumeRejectsUnknownToken() {
        when(tokens.findByHash(anyString())).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> service.consume("raw"));
    }

    @Test
    void consumeRejectsExpiredToken() {
        when(tokens.findByHash(anyString())).thenReturn(Optional.of(stored(NOW.minusSeconds(1), null)));
        assertThrows(InvalidCredentialsException.class, () -> service.consume("raw"));
        verify(tokens, never()).revokeAllForUser(anyLong(), any());
    }

    @Test
    void reusingARevokedTokenRevokesAllTokensOfTheUser() {
        when(tokens.findByHash(anyString())).thenReturn(Optional.of(stored(NOW.plusSeconds(60), NOW.minusSeconds(5))));
        assertThrows(InvalidCredentialsException.class, () -> service.consume("raw"));
        verify(tokens).revokeAllForUser(7L, NOW);
    }

    @Test
    void revokeMarksActiveToken() {
        RefreshToken token = stored(NOW.plusSeconds(60), null);
        when(tokens.findByHash(anyString())).thenReturn(Optional.of(token));
        service.revoke("raw");
        assertEquals(NOW, token.revokedAt);
    }

    @Test
    void revokeKeepsEarlierRevocationTime() {
        Instant earlier = NOW.minusSeconds(30);
        RefreshToken token = stored(NOW.plusSeconds(60), earlier);
        when(tokens.findByHash(anyString())).thenReturn(Optional.of(token));
        service.revoke("raw");
        assertEquals(earlier, token.revokedAt);
    }

    @Test
    void revokeIgnoresMissingToken() {
        service.revoke(null);
        service.revoke("");
        verifyNoInteractions(tokens);
    }

    @Test
    void revokeAllDelegatesToRepository() {
        service.revokeAll(7L);
        verify(tokens).revokeAllForUser(7L, NOW);
    }
}
