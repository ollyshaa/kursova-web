package com.ecoportal.security;

import com.ecoportal.domain.UserRole;

import java.io.Serializable;
import java.util.UUID;

/**
 * Principal, що зберігається в HTTP-сесії.
 */
public record AuthenticatedUser(UUID id, String email, String fullName, UserRole role) implements Serializable {
}
