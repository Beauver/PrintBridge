package com.beauver.printbridge.user;

import com.beauver.printbridge.auth.RefreshTokenService;
import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.common.error.ResourceNotFoundException;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import com.beauver.printbridge.user.dto.UserDto;
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

    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService();
        service.users = users;
        service.passwordHasher = passwordHasher;
        service.refreshTokens = refreshTokens;
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

    @Test
    void resetPasswordUpdatesHashAndLogsOutEverywhere() {
        User bob = user(3, "bob");
        when(users.findByIdOptional(3L)).thenReturn(Optional.of(bob));
        when(passwordHasher.hash("new-password")).thenReturn("new-hash");

        service.resetPassword(3L, "new-password");

        assertEquals("new-hash", bob.passwordHash);
        assertTrue(bob.credentialsChangedAt.isAfter(Instant.EPOCH));
        verify(refreshTokens).revokeAll(3L);
    }

    @Test
    void resetPasswordOfUnknownUserThrows() {
        when(users.findByIdOptional(3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.resetPassword(3L, "new-password"));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void deleteRemovesUser() {
        User bob = user(3, "bob");
        when(users.findByIdOptional(3L)).thenReturn(Optional.of(bob));
        service.delete(3L, 1L);
        verify(users).delete(bob);
    }

    @Test
    void cannotDeleteSelf() {
        assertThrows(ConflictException.class, () -> service.delete(1L, 1L));
        verify(users, never()).delete(any(User.class));
    }

    @Test
    void deleteUnknownUserThrows() {
        when(users.findByIdOptional(3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(3L, 1L));
    }
}
