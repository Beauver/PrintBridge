package com.beauver.printbridge.user;

import com.beauver.printbridge.user.dto.UserDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserMapper {

    public UserDto toDto(User user) {
        return new UserDto(user.id, user.username, user.email, user.role, user.createdAt);
    }
}
