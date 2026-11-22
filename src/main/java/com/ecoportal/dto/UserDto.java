package com.ecoportal.dto;

import com.ecoportal.domain.User;
import com.ecoportal.domain.UserRole;

import java.util.UUID;

public record UserDto(UUID id, String fullName, String email, UserRole role) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
