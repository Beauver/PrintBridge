package com.beauver.printbridge.auth;

import com.beauver.printbridge.user.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

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

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
