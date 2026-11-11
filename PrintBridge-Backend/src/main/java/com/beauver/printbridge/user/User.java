package com.beauver.printbridge.user;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 32)
    public String username;

    @Column(nullable = false, length = 254)
    public String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Role role;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    /** Access tokens issued before this moment are rejected (set on password reset). */
    @Column(name = "credentials_changed_at", nullable = false)
    public Instant credentialsChangedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (credentialsChangedAt == null) {
            credentialsChangedAt = now;
        }
    }
}
