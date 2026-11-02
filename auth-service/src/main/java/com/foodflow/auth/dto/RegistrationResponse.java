package com.foodflow.auth.dto;

import java.util.UUID;

public record RegistrationResponse(UUID userId, String fullName, String email, String role) {}
