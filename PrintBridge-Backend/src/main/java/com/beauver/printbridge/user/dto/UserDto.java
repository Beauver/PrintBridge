package com.beauver.printbridge.user.dto;

import com.beauver.printbridge.user.Role;

import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        String email,
        Role role,
        Instant createdAt) {
}
