package com.foodflow.orders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAddressRequest(
    @NotBlank @Size(max = 80) String label,
    @NotBlank @Size(max = 500) String address) {}
