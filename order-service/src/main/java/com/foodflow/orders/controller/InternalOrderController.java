package com.foodflow.orders.controller;

import com.foodflow.orders.entity.Order;
import com.foodflow.orders.repository.OrderRepository;
import com.foodflow.orders.service.OrderService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders/internal")
@RequiredArgsConstructor
public class InternalOrderController {
  private final OrderRepository repository;
  private final OrderService service;

  @GetMapping("/delivery-ready")
  @PreAuthorize("hasAnyRole('ADMIN','RESTAURANT_OWNER','DELIVERY_PARTNER')")
  public List<UUID> readyForDelivery() {
    return repository.findByStatus("READY_FOR_PICKUP").stream().map(Order::getId).toList();
  }

  @GetMapping("/{id}/delivery-check")
  @PreAuthorize("hasAnyRole('ADMIN','RESTAURANT_OWNER','DELIVERY_PARTNER')")
  public Map<String, Object> check(@PathVariable UUID id) {
    Order order = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!List.of("ACCEPTED", "PREPARING", "READY_FOR_PICKUP").contains(order.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Order is not ready for delivery assignment");
    }
    Map<String, Object> details = new HashMap<>();
    details.put("orderId", id);
    details.put("status", order.getStatus());
    details.put("paymentMethod", order.getPaymentMethod());
    details.put("deliveryAddress", order.getDeliveryAddress());
    return details;
  }

  @GetMapping("/{id}/review-check")
  @PreAuthorize("hasRole('CUSTOMER')")
  public Map<String, Object> reviewCheck(@PathVariable UUID id) {
    Order order = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    Map<String, Object> details = new HashMap<>();
    details.put("orderId", id);
    details.put("customerId", order.getCustomerId());
    details.put("restaurantId", order.getRestaurantId());
    details.put("status", order.getStatus());
    return details;
  }

  @RequestMapping(value = "/{id}/delivery-status", method = {RequestMethod.POST, RequestMethod.PATCH})
  @PreAuthorize("hasAnyRole('ADMIN','DELIVERY_PARTNER')")
  public void status(@PathVariable UUID id, @RequestParam String status,
      @RequestParam(defaultValue = "false") boolean cashCollected) {
    if (!List.of("OUT_FOR_DELIVERY", "DELIVERED").contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid delivery status");
    }
    service.updateStatus(id, status, cashCollected);
  }
}
