package com.foodflow.delivery.controller;

import com.foodflow.delivery.entity.Delivery;
import com.foodflow.delivery.entity.PartnerAvailability;
import com.foodflow.delivery.dto.DeliveryResponse;
import com.foodflow.delivery.service.DeliveryService;
import com.foodflow.delivery.service.PartnerAvailabilityService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {
  private final DeliveryService service;
  private final PartnerAvailabilityService availability;

  private UUID uid(Authentication authentication) {
    return UUID.fromString(authentication.getName());
  }

  @PostMapping("/{orderId}/assign")
  @PreAuthorize("hasAnyRole('ADMIN','RESTAURANT_OWNER')")
  @ResponseStatus(HttpStatus.CREATED)
  public Delivery assign(@PathVariable UUID orderId, @RequestParam UUID partnerId) {
    requireAvailable(partnerId);
    return service.assign(orderId, partnerId);
  }

  @PostMapping("/ready/{orderId}")
  @PreAuthorize("hasAnyRole('ADMIN','RESTAURANT_OWNER')")
  public Delivery createReadyDelivery(@PathVariable UUID orderId) {
    return service.createReadyDelivery(orderId);
  }

  @GetMapping("/mine")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  public List<DeliveryResponse> mine(Authentication authentication) {
    UUID partnerId = uid(authentication);
    return service.mine(partnerId).stream()
        .map(delivery -> DeliveryResponse.forPartner(delivery, partnerId))
        .toList();
  }

  @GetMapping("/availability")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  public PartnerAvailability myAvailability(Authentication authentication) {
    return availability.get(uid(authentication));
  }

  @PutMapping("/availability")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  public PartnerAvailability setAvailability(
      Authentication authentication, @RequestParam boolean available) {
    return availability.set(uid(authentication), available);
  }

  @PostMapping("/{id}/claim")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  public DeliveryResponse claim(Authentication authentication, @PathVariable UUID id) {
    UUID partnerId = uid(authentication);
    requireAvailable(partnerId);
    return DeliveryResponse.forPartner(service.claim(id, partnerId), partnerId);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasRole('DELIVERY_PARTNER')")
  public DeliveryResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestParam String status,
      @RequestParam(defaultValue = "false") boolean cashCollected) {
    UUID partnerId = uid(authentication);
    return DeliveryResponse.forPartner(
        service.update(id, partnerId, status, cashCollected), partnerId);
  }

  private void requireAvailable(UUID partnerId) {
    if (!availability.isAvailable(partnerId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Set your delivery availability to online before accepting work");
    }
  }
}
