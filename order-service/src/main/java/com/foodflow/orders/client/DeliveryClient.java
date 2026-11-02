package com.foodflow.orders.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
    name = "delivery-service",
    url = "${foodflow.delivery.url:}",
    configuration = com.foodflow.orders.config.FeignConfig.class)
public interface DeliveryClient {
  @PostMapping("/api/deliveries/ready/{orderId}")
  void createReadyDelivery(@PathVariable UUID orderId);
}
