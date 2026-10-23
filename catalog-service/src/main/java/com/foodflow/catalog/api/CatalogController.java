package com.foodflow.catalog.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService catalog;
    private final String internalToken;

    public CatalogController(CatalogService catalog,
                             @org.springframework.beans.factory.annotation.Value("${foodflow.internal-token}") String internalToken) {
        this.catalog = catalog;
        this.internalToken = internalToken;
    }

    @GetMapping("/restaurants")
    public Page<CatalogDtos.RestaurantResponse> restaurants(Pageable pageable) {
        return catalog.restaurants(pageable);
    }

    @GetMapping("/restaurants/{id}")
    public CatalogDtos.RestaurantResponse restaurant(@PathVariable UUID id) {
        return catalog.restaurant(id);
    }

    @PostMapping("/restaurants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public CatalogDtos.RestaurantResponse createRestaurant(@Valid @RequestBody CatalogDtos.RestaurantRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return catalog.createRestaurant(request, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/restaurants/{restaurantId}/items")
    public Page<CatalogDtos.FoodItemResponse> items(@PathVariable UUID restaurantId,
                                                    @RequestParam(required = false) String search,
                                                    Pageable pageable) {
        return catalog.items(restaurantId, search, pageable);
    }

    @GetMapping("/items/{id}")
    public CatalogDtos.FoodItemResponse item(@PathVariable UUID id) {
        return catalog.item(id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public CatalogDtos.FoodItemResponse createItem(@Valid @RequestBody CatalogDtos.FoodItemRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return catalog.createItem(request, UUID.fromString(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public CatalogDtos.FoodItemResponse updateItem(@PathVariable UUID id,
                                                   @Valid @RequestBody CatalogDtos.FoodItemRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return catalog.updateItem(id, request, UUID.fromString(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")));
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public void deleteItem(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        catalog.deleteItem(id, UUID.fromString(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")));
    }

    @PostMapping("/internal/stock/reservations")
    public CatalogDtos.StockResponse reserve(@RequestHeader("X-FoodFlow-Internal-Token") String token,
                                             @RequestBody @Valid CatalogDtos.StockRequest request) {
        verifyInternal(token);
        return catalog.reserve(request.itemId(), request.quantity());
    }

    @PostMapping("/internal/stock/releases")
    public CatalogDtos.StockResponse release(@RequestHeader("X-FoodFlow-Internal-Token") String token,
                                             @RequestBody @Valid CatalogDtos.StockRequest request) {
        verifyInternal(token);
        return catalog.release(request.itemId(), request.quantity());
    }

    private void verifyInternal(String supplied) {
        if (!java.security.MessageDigest.isEqual(supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                internalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid service credential");
        }
    }
}
