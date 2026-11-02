package com.foodflow.orders.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentResponse(
    UUID orderId,
    String razorpayOrderId,
    String keyId,
    BigDecimal amount,
    String currency) {}
