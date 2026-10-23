package com.foodflow.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<FoodOrder, UUID> {
    Optional<FoodOrder> findByIdempotencyKey(String idempotencyKey);
    Page<FoodOrder> findByCustomerId(UUID customerId, Pageable pageable);
    Page<FoodOrder> findByRestaurantId(UUID restaurantId, Pageable pageable);
}
