package com.beauver.printbridge.auth.token;

import com.beauver.printbridge.user.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;

/** A refresh token. Only the SHA-256 hash is stored; the raw value lives in the user's cookie. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    public User user;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    public String tokenHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;

    /**
     * Set together with {@link #revokedAt} when a refresh replaced this token with a new one.
     * Stays {@code null} for a token revoked by logout, and is cleared again when a logout or a
     * reuse happens later. (A password change deletes the user's tokens.)
     */
    @Column(name = "rotated_at")
    public Instant rotatedAt;

    /**
     * Marks the token as used up by a refresh that issues its successor. Two different fields:
     * {@link #revokedAt} ends the token, {@link #rotatedAt} records that the reason was a
     * rotation, which is what lets other tabs present it again for a moment.
     */
    public void replace(Instant now) {
        revokedAt = now;
        rotatedAt = now;
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /** True for a token that was replaced no longer than {@code grace} ago and hasn't expired. */
    public boolean wasJustRotated(Instant now, Duration grace) {
        return rotatedAt != null && !rotatedAt.plus(grace).isBefore(now) && expiresAt.isAfter(now);
    }
}
