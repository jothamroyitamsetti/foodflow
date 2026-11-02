package com.foodflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleApplicationRequest(
    @NotBlank @Pattern(regexp = "RESTAURANT_OWNER|DELIVERY_PARTNER") String requestedRole,
    @Size(max = 100) String restaurantName) {
}
