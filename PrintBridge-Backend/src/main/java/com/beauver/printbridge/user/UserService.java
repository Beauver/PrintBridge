package com.beauver.printbridge.user;

import com.beauver.printbridge.auth.RefreshTokenService;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.printrequest.PrintRequestService;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import com.beauver.printbridge.user.dto.UserDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class UserService {

    @Inject
    UserRepository users;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    UserMapper mapper;

    @Inject
    RefreshTokenService refreshTokens;

    @Inject
    PrintRequestService printRequests;

    public List<UserDto> getAll() {
        return users.listAllByUsername().stream().map(mapper::toDto).toList();
    }

    public UserDto get(long id) {
        return mapper.toDto(find(id));
    }

    /** Creates an account with the given role after checking username and email are free. */
    @Transactional
    public User create(NewAccountRequest request, Role role) {
        String username = request.username().trim();
        String email = request.email().trim();
        if (users.usernameExists(username)) {
            throw new ConflictException("Username is already taken");
        }
        if (users.emailExists(email)) {
            throw new ConflictException("Email is already in use");
        }
        User user = new User();
        user.username = username;
        user.email = email;
        user.passwordHash = passwordHasher.hash(request.password());
        user.role = role;
        users.persist(user);
        return user;
    }

    @Transactional
    public UserDto createUser(NewAccountRequest request) {
        return mapper.toDto(create(request, Role.USER));
    }

    /** Sets a new password and logs the user out everywhere (old access and refresh tokens stop working). */
    @Transactional
    public void resetPassword(long id, String newPassword) {
        User user = find(id);
        user.passwordHash = passwordHasher.hash(newPassword);
        user.credentialsChangedAt = Instant.now();
        refreshTokens.revokeAll(id);
    }

    /**
     * Deletes a user. Their refresh tokens and print requests go with them (ON DELETE CASCADE);
     * the request files are removed from disk once the transaction commits.
     */
    @Transactional
    public void delete(long id, long actingUserId) {
        if (id == actingUserId) {
            throw new ConflictException("You cannot delete your own account");
        }
        User user = find(id);
        printRequests.deleteFilesOfOwner(id);
        users.delete(user);
    }

    private User find(long id) {
        return users.findByIdOptional(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
