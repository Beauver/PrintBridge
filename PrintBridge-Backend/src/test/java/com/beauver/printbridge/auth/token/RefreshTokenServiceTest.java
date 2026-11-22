package com.beauver.printbridge.auth.token;

import com.beauver.printbridge.auth.AuthConfig;
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
        lenient().when(config.refreshGrace()).thenReturn(Duration.ofSeconds(10));
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
    void issueDeletesTheExpiredTokensOfTheUser() {
        when(config.refreshTokenTtl()).thenReturn(Duration.ofDays(14));
        service.issue(user);
        verify(tokens).deleteExpiredForUser(7L, NOW);
    }

    @Test
    void issueCreatesDifferentTokens() {
        when(config.refreshTokenTtl()).thenReturn(Duration.ofDays(14));
        assertNotEquals(service.issue(user), service.issue(user));
    }

    @Test
    void consumeRevokesAndReturnsUser() {
        RefreshToken token = stored(NOW.plusSeconds(60), null);
        when(tokens.findByHashForUpdate(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));

        assertSame(user, service.consume("raw"));
        assertEquals(NOW, token.revokedAt);
        assertEquals(NOW, token.rotatedAt);
    }

    @Test
    void consumeAcceptsATokenThatWasJustReplaced() {
        RefreshToken token = stored(NOW.plusSeconds(60), NOW.minusSeconds(10));
        token.rotatedAt = NOW.minusSeconds(10);
        when(tokens.findByHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertSame(user, service.consume("raw"));
        assertSame(user, service.consume("raw"));
        verify(tokens, never()).revokeAllForUser(anyLong(), any());
        assertEquals(NOW.minusSeconds(10), token.revokedAt);
    }

    @Test
    void consumeTreatsAReplacedTokenAsStolenAfterTheGracePeriod() {
        RefreshToken token = stored(NOW.plusSeconds(60), NOW.minusSeconds(11));
        token.rotatedAt = NOW.minusSeconds(11);
        when(tokens.findByHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidCredentialsException.class, () -> service.consume("raw"));
        verify(tokens).revokeAllForUser(7L, NOW);
    }

    @Test
    void consumeRejectsAnExpiredTokenEvenWhenJustReplaced() {
        RefreshToken token = stored(NOW.minusSeconds(1), NOW.minusSeconds(2));
        token.rotatedAt = NOW.minusSeconds(2);
        when(tokens.findByHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertThrows(InvalidCredentialsException.class, () -> service.consume("raw"));
    }

    @Test
    void consumeRejectsMissingToken() {
        assertThrows(InvalidCredentialsException.class, () -> service.consume(null));
        assertThrows(InvalidCredentialsException.class, () -> service.consume(" "));
        verifyNoInteractions(tokens);
    }

    @Test
    void consumeRejectsUnknownToken() {
        when(tokens.findByHashForUpdate(anyString())).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class, () -> service.consume("raw"));
    }

    @Test
    void consumeRejectsExpiredToken() {
        when(tokens.findByHashForUpdate(anyString())).thenReturn(Optional.of(stored(NOW.minusSeconds(1), null)));
        assertThrows(InvalidCredentialsException.class, () -> service.consume("raw"));
        verify(tokens, never()).revokeAllForUser(anyLong(), any());
    }

    @Test
    void reusingARevokedTokenRevokesAllTokensOfTheUser() {
        when(tokens.findByHashForUpdate(anyString())).thenReturn(Optional.of(stored(NOW.plusSeconds(60), NOW.minusSeconds(5))));
        assertThrows(InvalidCredentialsException.class, () -> service.consume("raw"));
        verify(tokens).revokeAllForUser(7L, NOW);
    }

    @Test
    void revokeMarksActiveToken() {
        RefreshToken token = stored(NOW.plusSeconds(60), null);
        when(tokens.findByHash(anyString())).thenReturn(Optional.of(token));
        service.revoke("raw");
        assertEquals(NOW, token.revokedAt);
        verify(tokens).endGraceForUser(7L);
    }

    @Test
    void revokeIgnoresUnknownToken() {
        when(tokens.findByHash(anyString())).thenReturn(Optional.empty());
        service.revoke("raw");
        verify(tokens, never()).endGraceForUser(anyLong());
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
    void deleteAllDelegatesToRepository() {
        service.deleteAll(7L);
        verify(tokens).deleteAllForUser(7L);
    }
}
