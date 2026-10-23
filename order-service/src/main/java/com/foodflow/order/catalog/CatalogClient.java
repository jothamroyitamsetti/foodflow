package com.foodflow.order.catalog;

import jakarta.validation.constraints.Positive;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "catalog-service", configuration = CatalogClientConfig.class)
public interface CatalogClient {
    @GetMapping("/api/catalog/items/{id}")
    FoodItemResponse getItem(@PathVariable UUID id);

    @GetMapping("/api/catalog/restaurants/{id}")
    RestaurantResponse getRestaurant(@PathVariable UUID id);

    @PostMapping("/api/catalog/internal/stock/reservations")
    StockResponse reserve(@RequestHeader("X-FoodFlow-Internal-Token") String token, @RequestBody StockRequest request);

    @PostMapping("/api/catalog/internal/stock/releases")
    StockResponse release(@RequestHeader("X-FoodFlow-Internal-Token") String token, @RequestBody StockRequest request);

    record FoodItemResponse(UUID id, UUID restaurantId, String name, String description,
                            BigDecimal price, boolean available, int stockQuantity) {}
    record RestaurantResponse(UUID id, String name, String address, UUID ownerId, boolean active) {}
    record StockRequest(UUID itemId, @Positive int quantity) {}
    record StockResponse(UUID itemId, int remainingStock) {}
}
