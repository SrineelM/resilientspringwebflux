package com.resilient.security;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Separate table for authentication credentials (username + password hash + roles). */
@Table("user_credentials")
public record UserCredentials(@Id Long id, String username, String passwordHash, String roles, boolean enabled) {}
