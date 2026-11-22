package com.beauver.printbridge.user;

import com.beauver.printbridge.auth.token.RefreshTokenService;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.InvalidInputException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import com.beauver.printbridge.user.dto.UpdateAccountRequest;
import com.beauver.printbridge.user.dto.UpdateUserRequest;
import com.beauver.printbridge.user.dto.UserDto;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository users;
    @Mock
    PasswordHasher passwordHasher;
    @Mock
    RefreshTokenService refreshTokens;
    @Mock
    Event<UserDeleted> userDeleted;

    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService();
        service.users = users;
        service.passwordHasher = passwordHasher;
        service.refreshTokens = refreshTokens;
        service.userDeleted = userDeleted;
        service.mapper = new UserMapper();
    }

    private static User user(long id, String username) {
        User u = new User();
        u.id = id;
        u.username = username;
        u.email = username + "@example.com";
        u.role = Role.USER;
        u.createdAt = Instant.EPOCH;
        u.credentialsChangedAt = Instant.EPOCH;
        return u;
    }

    @Test
    void listMapsToDtos() {
        when(users.listAllByUsername()).thenReturn(List.of(user(1, "a"), user(2, "b")));
        List<UserDto> result = service.getAll();
        assertEquals(List.of("a", "b"), result.stream().map(UserDto::username).toList());
    }

    @Test
    void getUnknownUserThrowsNotFound() {
        when(users.findByIdOptional(5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(5L));
    }

    @Test
    void createTrimsHashesAndPersists() {
        when(passwordHasher.hash("password123")).thenReturn("hashed");
        UserDto dto = service.createUser(new NewAccountRequest(" bob ", " bob@example.com ", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).persist(captor.capture());
        User saved = captor.getValue();
        assertEquals("bob", saved.username);
        assertEquals("bob@example.com", saved.email);
        assertEquals("hashed", saved.passwordHash);
        assertEquals(Role.USER, saved.role);
        assertEquals("bob", dto.username());
    }

    @Test
    void createRejectsTakenUsername() {
        when(users.usernameExists("bob")).thenReturn(true);
        var e = assertThrows(ConflictException.class,
                () -> service.createUser(new NewAccountRequest("bob", "x@example.com", "password123")));
        assertEquals("Username is already taken", e.getMessage());
        verify(users, never()).persist(any(User.class));
    }

    @Test
    void createRejectsTakenEmail() {
        when(users.emailExists("x@example.com")).thenReturn(true);
        var e = assertThrows(ConflictException.class,
                () -> service.createUser(new NewAccountRequest("bob", "x@example.com", "password123")));
        assertEquals("Email is already in use", e.getMessage());
    }

    private static UpdateUserRequest update(String username, String email, boolean changePassword, String password) {
        return new UpdateUserRequest(username, email, changePassword, password);
    }

    private static UpdateAccountRequest updateOwn(
            String username,
            String email,
            boolean changePassword,
            String password,
            String currentPassword) {
        return new UpdateAccountRequest(username, email, changePassword, password, currentPassword);
    }

    @Test
    void updateUserTrimsAndKeepsSessionsWhenPasswordStaysTheSame() {
        User bob = user(3, "bob");
        bob.passwordHash = "old-hash";
        when(users.findByIdOptional(3L)).thenReturn(Optional.of(bob));

        UserDto dto = service.updateUser(3L, update(" robert ", " robert@example.com ", false, null), 1L);

        assertEquals("robert", bob.username);
        assertEquals("robert@example.com", bob.email);
        assertEquals("old-hash", bob.passwordHash);
        assertEquals(Instant.EPOCH, bob.credentialsChangedAt);
        assertEquals("robert", dto.username());
        verifyNoInteractions(refreshTokens);
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    void updateUserWithNewPasswordLogsOutEverywhere() {
        User bob = user(3, "bob");
        when(users.findByIdOptional(3L)).thenReturn(Optional.of(bob));
        when(passwordHasher.hash("new-password")).thenReturn("new-hash");

        service.updateUser(3L, update("bob", "bob@example.com", true, "new-password"), 1L);

        assertEquals("new-hash", bob.passwordHash);
        assertTrue(bob.credentialsChangedAt.isAfter(Instant.EPOCH));
        verify(refreshTokens).deleteAll(3L);
    }

    @Test
    void updateUserChecksUniquenessAgainstOtherAccountsOnly() {
        User bob = user(3, "bob");
        when(users.findByIdOptional(3L)).thenReturn(Optional.of(bob));
        when(users.usernameTakenByOther("alice", 3L)).thenReturn(true);
        var usernameError = assertThrows(ConflictException.class,
                () -> service.updateUser(3L, update("alice", "bob@example.com", false, null), 1L));
        assertEquals("Username is already taken", usernameError.getMessage());

        when(users.emailTakenByOther("alice@example.com", 3L)).thenReturn(true);
        var emailError = assertThrows(ConflictException.class,
                () -> service.updateUser(3L, update("bob", "alice@example.com", false, null), 1L));
        assertEquals("Email is already in use", emailError.getMessage());
        assertEquals("bob", bob.username);
        assertEquals("bob@example.com", bob.email);
    }

    @Test
    void updateUserOfUnknownUserThrows() {
        when(users.findByIdOptional(3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateUser(3L, update("bob", "bob@example.com", true, "new-password"), 1L));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void adminCannotUpdateThemselvesWithoutTheirCurrentPassword() {
        var e = assertThrows(ConflictException.class,
                () -> service.updateUser(1L, update("admin", "admin@example.com", false, null), 1L));
        assertEquals("Update your own account on the My account page", e.getMessage());
        verify(users, never()).findByIdOptional(any());
    }

    @Test
    void updateOwnAccountRequiresTheCurrentPassword() {
        User bob = user(3, "bob");
        bob.passwordHash = "hash";
        when(users.findByIdOptional(3L)).thenReturn(Optional.of(bob));
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        var e = assertThrows(InvalidInputException.class,
                () -> service.updateOwnAccount(3L, updateOwn("robert", "bob@example.com", false, null, "wrong")));

        assertEquals("Current password is incorrect", e.getMessage());
        assertEquals("bob", bob.username);
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void updateOwnAccountChangesDetailsAndPassword() {
        User bob = user(3, "bob");
        bob.passwordHash = "hash";
        when(users.findByIdOptional(3L)).thenReturn(Optional.of(bob));
        when(passwordHasher.matches("current", "hash")).thenReturn(true);
        when(passwordHasher.hash("new-password")).thenReturn("new-hash");

        User result = service.updateOwnAccount(
                3L, updateOwn("robert", "robert@example.com", true, "new-password", "current"));

        assertSame(bob, result);
        assertEquals("robert", bob.username);
        assertEquals("robert@example.com", bob.email);
        assertEquals("new-hash", bob.passwordHash);
        verify(refreshTokens).deleteAll(3L);
    }

    @Test
    void updateOwnAccountWithoutPasswordChangeKeepsSessions() {
        User bob = user(3, "bob");
        bob.passwordHash = "hash";
        when(users.findByIdOptional(3L)).thenReturn(Optional.of(bob));
        when(passwordHasher.matches("current", "hash")).thenReturn(true);

        service.updateOwnAccount(3L, updateOwn("bob", "new@example.com", false, null, "current"));

        assertEquals("new@example.com", bob.email);
        assertEquals("hash", bob.passwordHash);
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void deleteRemovesUserAndTheirRequestFiles() {
        User bob = user(3, "bob");
        when(users.findByIdOptional(3L)).thenReturn(Optional.of(bob));
        service.delete(3L, 1L);
        verify(userDeleted).fire(new UserDeleted(3L));
        verify(users).delete(bob);
    }

    @Test
    void cannotDeleteSelf() {
        assertThrows(ConflictException.class, () -> service.delete(1L, 1L));
        verify(users, never()).delete(any(User.class));
        verifyNoInteractions(userDeleted);
    }

    @Test
    void deleteUnknownUserThrows() {
        when(users.findByIdOptional(3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(3L, 1L));
    }
}
