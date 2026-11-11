package com.beauver.printbridge.auth;

import com.beauver.printbridge.common.error.ConflictException;
import com.beauver.printbridge.user.Role;
import com.beauver.printbridge.user.User;
import com.beauver.printbridge.user.UserRepository;
import com.beauver.printbridge.user.UserService;
import com.beauver.printbridge.user.dto.NewAccountRequest;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetupServiceTest {

    @Mock
    UserRepository users;
    @Mock
    UserService userService;
    @Mock
    AuthService authService;

    SetupService service;
    final NewAccountRequest request = new NewAccountRequest("owner", "owner@example.com", "password123");

    @BeforeEach
    void setUp() {
        service = new SetupService();
        service.users = users;
        service.userService = userService;
        service.authService = authService;
    }

    @Test
    void setupRequiredOnlyWithoutAdmin() {
        when(users.adminExists()).thenReturn(false, true);
        assertTrue(service.isSetupRequired());
        assertFalse(service.isSetupRequired());
    }

    @Test
    void createsAdminAndStartsSession() {
        User admin = new User();
        AuthSession session = new AuthSession(null, "refresh");
        when(users.adminExists()).thenReturn(false);
        when(userService.create(request, Role.ADMIN)).thenReturn(admin);
        when(authService.startSession(admin)).thenReturn(session);

        assertSame(session, service.createAdmin(request));
    }

    @Test
    void refusesWhenAdminExists() {
        when(users.adminExists()).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.createAdmin(request));
        verifyNoInteractions(userService);
    }

    @Test
    void concurrentSetupLosesWithConflict() {
        when(users.adminExists()).thenReturn(false);
        when(userService.create(request, Role.ADMIN)).thenReturn(new User());
        doThrow(new PersistenceException("unique violation")).when(users).flush();

        assertThrows(ConflictException.class, () -> service.createAdmin(request));
        verifyNoInteractions(authService);
    }
}
