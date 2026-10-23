package com.foodflow.delivery.api;

import com.foodflow.delivery.domain.DeliveryStatus;
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
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private final DeliveryService deliveries;

    public DeliveryController(DeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    @PostMapping("/orders/{orderId}/assign")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DeliveryDtos.DeliveryResponse assign(@PathVariable UUID orderId) {
        return deliveries.assign(orderId);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public Page<DeliveryDtos.DeliveryResponse> mine(Pageable pageable, @AuthenticationPrincipal Jwt jwt) {
        return deliveries.myDeliveries(UUID.fromString(jwt.getSubject()), pageable);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public DeliveryDtos.DeliveryResponse updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody DeliveryDtos.StatusRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return deliveries.updateStatus(id, UUID.fromString(jwt.getSubject()), request.status());
    }
}
