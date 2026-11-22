package com.beauver.printbridge.user;

import com.beauver.printbridge.auth.token.RefreshTokenService;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import com.beauver.printbridge.user.dto.UpdateAccountRequest;
import com.beauver.printbridge.user.dto.UpdateUserRequest;
import com.beauver.printbridge.user.dto.UserDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
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

    /** Lets the owners of a user's data outside the database (request files) clean up. */
    @Inject
    Event<UserDeleted> userDeleted;

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

    /**
     * An admin updating another user's details. Changing the password logs that user out
     * everywhere; changing only the username or email keeps their sessions.
     * <p>
     * Admins update their own account through {@link #updateOwnAccount}, which asks for the
     * current password.
     */
    @Transactional
    public UserDto updateUser(long id, UpdateUserRequest request, long actingUserId) {
        if (id == actingUserId) {
            throw new ConflictException("Update your own account on the My account page");
        }
        User user = find(id);
        updateDetails(user, request.username(), request.email());
        if (request.changePassword()) {
            changePassword(user, request.newPassword());
        }
        return mapper.toDto(user);
    }

    /**
     * A user updating their own account after confirming their current password. Changing the
     * password logs out all their sessions; the caller starts a new one for the current browser.
     *
     * @throws InvalidInputException when the current password is wrong
     */
    @Transactional
    public User updateOwnAccount(long id, UpdateAccountRequest request) {
        User user = find(id);
        if (!passwordHasher.matches(request.currentPassword(), user.passwordHash)) {
            throw new InvalidInputException("Current password is incorrect");
        }
        updateDetails(user, request.username(), request.email());
        if (request.changePassword()) {
            changePassword(user, request.newPassword());
        }
        return user;
    }

    /** Checks that another account doesn't use the username or email; keeping your own is fine. */
    private void updateDetails(User user, String username, String email) {
        String trimmedUsername = username.trim();
        String trimmedEmail = email.trim();
        if (users.usernameTakenByOther(trimmedUsername, user.id)) {
            throw new ConflictException("Username is already taken");
        }
        if (users.emailTakenByOther(trimmedEmail, user.id)) {
            throw new ConflictException("Email is already in use");
        }
        user.username = trimmedUsername;
        user.email = trimmedEmail;
    }

    /** Sets a new password and logs the user out everywhere (old access and refresh tokens stop working). */
    private void changePassword(User user, String newPassword) {
        user.passwordHash = passwordHasher.hash(newPassword);
        user.credentialsChangedAt = Instant.now();
        refreshTokens.deleteAll(user.id);
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
        userDeleted.fire(new UserDeleted(id));
        users.delete(user);
    }

    private User find(long id) {
        return users.findByIdOptional(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
