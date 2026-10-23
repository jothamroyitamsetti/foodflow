package com.foodflow.delivery.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderClient {
    @GetMapping("/api/orders/internal/{id}/delivery-eligibility")
    EligibilityResponse eligibility(@RequestHeader("X-FoodFlow-Internal-Token") String token, @PathVariable UUID id);

    @PatchMapping("/api/orders/internal/{id}/status")
    void updateStatus(@RequestHeader("X-FoodFlow-Internal-Token") String token,
                      @PathVariable UUID id, @RequestBody StatusRequest request);

    record EligibilityResponse(UUID orderId, UUID customerId, boolean eligible) {}
    record StatusRequest(String status) {}
}
