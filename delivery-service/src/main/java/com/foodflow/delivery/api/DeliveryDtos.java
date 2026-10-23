package com.foodflow.delivery.api;

import com.foodflow.delivery.domain.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class DeliveryDtos {
    private DeliveryDtos() {}
    public record StatusRequest(@NotNull DeliveryStatus status) {}
    public record DeliveryResponse(UUID id, UUID orderId, UUID partnerId, DeliveryStatus status, Instant assignedAt) {}
}
