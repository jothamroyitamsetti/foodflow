package com.foodflow.delivery.dto;

import com.foodflow.delivery.entity.Delivery;
import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
    UUID id,
    UUID orderId,
    UUID partnerId,
    String status,
    String paymentMethod,
    String deliveryAddress,
    Instant cashCollectedAt,
    Instant assignedAt) {
  public static DeliveryResponse forPartner(Delivery delivery, UUID partnerId) {
    boolean assignedToPartner = delivery.getPartnerId().equals(partnerId)
        && !"AVAILABLE".equals(delivery.getStatus());
    return new DeliveryResponse(
        delivery.getId(),
        delivery.getOrderId(),
        delivery.getPartnerId(),
        delivery.getStatus(),
        delivery.getPaymentMethod(),
        assignedToPartner ? delivery.getDeliveryAddress() : null,
        delivery.getCashCollectedAt(),
        delivery.getAssignedAt());
  }
}
