package com.beauver.printbridge.auth;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class RefreshTokenRepository implements PanacheRepository<RefreshToken> {

    public Optional<RefreshToken> findByHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    public void revokeAllForUser(long userId, Instant now) {
        update("revokedAt = ?1 where user.id = ?2 and revokedAt is null", now, userId);
    }
}
