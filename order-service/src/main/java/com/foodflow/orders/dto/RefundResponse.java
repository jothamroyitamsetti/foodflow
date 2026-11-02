package com.foodflow.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
    UUID orderId,
    String refundId,
    BigDecimal amount,
    String status,
    Instant updatedAt) {}
