package com.beauver.printbridge.user;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    /** Finds a user by username or email, both case-insensitive. */
    public Optional<User> findByLogin(String login) {
        return find("lower(username) = lower(?1) or lower(email) = lower(?1)", login).firstResultOptional();
    }

    public boolean usernameExists(String username) {
        return count("lower(username) = lower(?1)", username) > 0;
    }

    public boolean emailExists(String email) {
        return count("lower(email) = lower(?1)", email) > 0;
    }

    public boolean adminExists() {
        return count("role", Role.ADMIN) > 0;
    }

    public List<User> listAllByUsername() {
        return listAll(Sort.by("username"));
    }
}
