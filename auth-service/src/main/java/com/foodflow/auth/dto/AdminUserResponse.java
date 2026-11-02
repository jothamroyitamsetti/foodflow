package com.foodflow.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(UUID id, String fullName, String email, String role, Instant createdAt) {}
