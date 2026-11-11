package com.beauver.printbridge.auth;

import com.beauver.printbridge.common.error.InvalidCredentialsException;
import com.beauver.printbridge.user.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes refresh tokens.
 * <p>
 * Every refresh replaces the token. If a token that was already replaced or revoked is presented
 * again, it has probably been stolen, so all of that user's tokens are revoked.
 */
@ApplicationScoped
public class RefreshTokenService {

    static final String INVALID = "Session expired, please log in again";

    private final SecureRandom random = new SecureRandom();

    @Inject
    RefreshTokenRepository tokens;

    @Inject
    AuthConfig config;

    Clock clock = Clock.systemUTC();

    /** Creates a new token for the user and returns its raw value. */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        RefreshToken token = new RefreshToken();
        token.user = user;
        token.tokenHash = hash(raw);
        token.createdAt = now;
        token.expiresAt = now.plus(config.refreshTokenTtl());
        tokens.persist(token);
        return raw;
    }

    /**
     * Revokes the presented token and returns its user, so the caller can issue a new pair.
     *
     * @throws InvalidCredentialsException when the token is unknown, expired or already used
     */
    @Transactional(dontRollbackOn = InvalidCredentialsException.class)
    public User consume(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidCredentialsException(INVALID);
        }
        Instant now = clock.instant();
        RefreshToken token = tokens.findByHash(hash(raw)).orElseThrow(() -> new InvalidCredentialsException(INVALID));
        if (token.revokedAt != null) {
            // Reuse of a rotated token: treat the whole session family as compromised.
            tokens.revokeAllForUser(token.user.id, now);
            throw new InvalidCredentialsException(INVALID);
        }
        if (!token.isUsable(now)) {
            throw new InvalidCredentialsException(INVALID);
        }
        token.revokedAt = now;
        return token.user;
    }

    /** Revokes one token (logout). Unknown tokens are ignored. */
    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        tokens.findByHash(hash(raw)).filter(t -> t.revokedAt == null).ifPresent(t -> t.revokedAt = clock.instant());
    }

    /** Revokes every active token of a user (password reset). */
    @Transactional
    public void revokeAll(long userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
