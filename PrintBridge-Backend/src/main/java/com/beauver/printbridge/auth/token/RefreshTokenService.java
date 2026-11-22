package com.beauver.printbridge.auth.token;

import com.beauver.printbridge.auth.AuthConfig;
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
 * <p>
 * The exception is a token replaced a moment ago ({@code AuthConfig#refreshGrace}): browser tabs
 * that load together all send the same cookie, so that is normal use. Each of them gets its own
 * new token (only hashes are stored, so the first one can't be handed out again); the browser
 * keeps the cookie that arrives last and the others expire unused.
 */
@ApplicationScoped
public class RefreshTokenService {

    public static final String INVALID = "Session expired, please log in again";

    private final SecureRandom random = new SecureRandom();

    @Inject
    RefreshTokenRepository tokens;

    @Inject
    AuthConfig config;

    Clock clock = Clock.systemUTC();

    /**
     * Creates a new token for the user and returns its raw value. Also deletes the user's expired
     * tokens: every page load adds one, and this keeps the table from growing without a job.
     */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        tokens.deleteExpiredForUser(user.id, now);
        RefreshToken token = new RefreshToken();
        token.user = user;
        token.tokenHash = hash(raw);
        token.createdAt = now;
        token.expiresAt = now.plus(config.refreshTokenTtl());
        tokens.persist(token);
        return raw;
    }

    /**
     * Revokes the presented token and returns its user, so the caller can issue a new pair. The
     * token's row stays locked until the caller's transaction ends.
     *
     * @throws InvalidCredentialsException when the token is unknown, expired, revoked, or was
     *                                     replaced longer ago than the grace period
     */
    @Transactional(dontRollbackOn = InvalidCredentialsException.class)
    public User consume(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidCredentialsException(INVALID);
        }
        Instant now = clock.instant();
        RefreshToken token = tokens.findByHashForUpdate(hash(raw))
                .orElseThrow(() -> new InvalidCredentialsException(INVALID));
        if (token.wasJustRotated(now, config.refreshGrace())) {
            // Another tab refreshed with the same cookie a moment ago.
            return token.user;
        }
        if (token.revokedAt != null) {
            // Reuse of a rotated token: treat the whole session family as compromised.
            tokens.revokeAllForUser(token.user.id, now);
            throw new InvalidCredentialsException(INVALID);
        }
        if (!token.isUsable(now)) {
            throw new InvalidCredentialsException(INVALID);
        }
        token.replace(now);
        return token.user;
    }

    /**
     * Revokes one token (logout). Unknown tokens are ignored. The tokens it replaced a moment ago
     * stop being accepted too, so logging out really ends the session in this browser.
     */
    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        RefreshToken token = tokens.findByHash(hash(raw)).orElse(null);
        if (token == null) {
            return;
        }
        if (token.revokedAt == null) {
            token.revokedAt = clock.instant();
        }
        tokens.endGraceForUser(token.user.id);
    }

    /**
     * Ends every session of a user after a password change by deleting their tokens. Deleted, not
     * revoked: a revoked token presented again looks like theft and would revoke all tokens,
     * including the session started right after the change (own password change). A deleted one
     * is just unknown.
     */
    @Transactional
    public void deleteAll(long userId) {
        tokens.deleteAllForUser(userId);
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
