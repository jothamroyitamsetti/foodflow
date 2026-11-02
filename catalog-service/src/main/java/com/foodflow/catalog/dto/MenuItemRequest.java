package com.foodflow.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemRequest(@NotBlank @Size(max = 100) String restaurantName, @NotBlank @Size(max = 120) String name,
		@Size(max = 1000) String description, @NotNull @DecimalMin("0.01") BigDecimal price,
		@Min(0) int availableQuantity, UUID restaurantId) {
	public MenuItemRequest(String restaurantName, String name, String description, BigDecimal price, int availableQuantity) {
		this(restaurantName, name, description, price, availableQuantity, null);
	}
}
