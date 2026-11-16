package com.beauver.printbridge.auth.token;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class RefreshTokenRepository implements PanacheRepository<RefreshToken> {

    public Optional<RefreshToken> findByHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    /**
     * Like {@link #findByHash}, but locks the row until the transaction ends. Two refreshes with
     * the same token then run one after the other, and the second sees what the first did.
     */
    public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
        return find("tokenHash", tokenHash).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    /** Revokes every active token of the user; none of their replaced tokens is accepted any more either. */
    public void revokeAllForUser(long userId, Instant now) {
        update("revokedAt = ?1 where user.id = ?2 and revokedAt is null", now, userId);
        endGraceForUser(userId);
    }

    /** Tokens of the user that were just replaced are no longer accepted (see {@code wasJustRotated}). */
    public void endGraceForUser(long userId) {
        update("rotatedAt = null where user.id = ?1 and rotatedAt is not null", userId);
    }

    /** Expired tokens can't be used for anything, not even to detect reuse. */
    public long deleteExpiredForUser(long userId, Instant now) {
        return delete("user.id = ?1 and expiresAt < ?2", userId, now);
    }
}
