package com.foodflow.review.dto;

import java.util.UUID;

public record ReviewStatusResponse(
    UUID orderId,
    boolean reviewed,
    Integer rating,
    String comment) {}
