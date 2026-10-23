package com.foodflow.catalog.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record RestaurantRequest(@NotBlank @Size(max = 120) String name,
                                    @NotBlank @Size(max = 300) String address) {}
    public record RestaurantResponse(UUID id, String name, String address, UUID ownerId, boolean active) {}
    public record FoodItemRequest(@NotNull UUID restaurantId, @NotBlank @Size(max = 120) String name,
                                  @Size(max = 1000) String description,
                                  @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
                                  @PositiveOrZero int stockQuantity) {}
    public record FoodItemResponse(UUID id, UUID restaurantId, String name, String description,
                                   BigDecimal price, boolean available, int stockQuantity) {}
    public record StockRequest(@NotNull UUID itemId, @Positive int quantity) {}
    public record StockResponse(UUID itemId, int remainingStock) {}
}
