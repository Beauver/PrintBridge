package com.beauver.printbridge.auth.dto;

import com.beauver.printbridge.user.dto.UserDto;

public record AuthResponse(String accessToken, long expiresIn, UserDto user) {
}
