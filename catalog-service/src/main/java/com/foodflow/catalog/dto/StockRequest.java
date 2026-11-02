package com.foodflow.catalog.dto;

import jakarta.validation.constraints.Min;

public record StockRequest(@Min(1) int quantity) {
}
