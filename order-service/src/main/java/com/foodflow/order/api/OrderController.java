package com.foodflow.order.api;

import com.foodflow.order.domain.OrderStatus;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    private final String internalToken;

    public OrderController(OrderService orders, @Value("${foodflow.internal-token}") String internalToken) {
        this.orders = orders;
        this.internalToken = internalToken;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderDtos.OrderResponse create(@RequestHeader("Idempotency-Key") String key,
                                          @Valid @RequestBody OrderDtos.CreateOrderRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        return orders.create(UUID.fromString(jwt.getSubject()), key, request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<OrderDtos.OrderResponse> history(Pageable pageable, @AuthenticationPrincipal Jwt jwt) {
        return orders.history(UUID.fromString(jwt.getSubject()), pageable);
    }

    @GetMapping("/restaurant/{restaurantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public Page<OrderDtos.OrderResponse> restaurantOrders(@PathVariable UUID restaurantId, Pageable pageable,
                                                          @AuthenticationPrincipal Jwt jwt) {
        return orders.restaurantOrders(restaurantId, UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("role"), pageable);
    }

    @GetMapping("/{id}")
    public OrderDtos.OrderResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return orders.get(id, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public OrderDtos.OrderResponse updateStatus(@PathVariable UUID id,
                                                @Valid @RequestBody OrderDtos.InternalStatusRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return orders.updateRestaurantStatus(id, UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("role"), request.status());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderDtos.OrderResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return orders.cancel(id, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/internal/{id}/delivery-eligibility")
    public OrderDtos.DeliveryEligibilityResponse eligibility(@RequestHeader("X-FoodFlow-Internal-Token") String token,
                                                              @PathVariable UUID id) {
        verifyInternal(token);
        return orders.deliveryEligibility(id);
    }

    @PatchMapping("/internal/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@RequestHeader("X-FoodFlow-Internal-Token") String token, @PathVariable UUID id,
                             @Valid @RequestBody OrderDtos.InternalStatusRequest request) {
        verifyInternal(token);
        orders.updateInternalStatus(id, request.status());
    }

    private void verifyInternal(String supplied) {
        if (!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), internalToken.getBytes(StandardCharsets.UTF_8))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid service credential");
        }
    }
}
