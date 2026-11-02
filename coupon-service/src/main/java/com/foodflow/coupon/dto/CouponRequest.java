package com.foodflow.coupon.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CouponRequest(
    @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
    @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal discountPercent,
    @Min(1) int maxUses,
    @NotNull LocalDateTime expiresAt) {}
