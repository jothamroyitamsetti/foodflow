package com.foodflow.order.api;

import com.foodflow.order.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {
    private OrderDtos() {}
    public record CreateOrderRequest(@NotEmpty List<@Valid LineRequest> items) {}
    public record LineRequest(@NotNull UUID itemId, @Positive int quantity) {}
    public record LineResponse(UUID itemId, String itemName, int quantity, BigDecimal unitPrice) {}
    public record StatusEventResponse(OrderStatus status, Instant changedAt) {}
    public record OrderResponse(UUID id, UUID customerId, BigDecimal total, OrderStatus status,
                                Instant createdAt, List<LineResponse> items, List<StatusEventResponse> statusHistory) {}
    public record DeliveryEligibilityResponse(UUID orderId, UUID customerId, boolean eligible) {}
    public record InternalStatusRequest(OrderStatus status) {}
}
