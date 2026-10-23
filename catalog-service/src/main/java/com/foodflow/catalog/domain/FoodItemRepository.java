package com.foodflow.catalog.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FoodItemRepository extends JpaRepository<FoodItem, UUID> {
    Page<FoodItem> findByRestaurantIdAndAvailableTrue(UUID restaurantId, Pageable pageable);
    Page<FoodItem> findByRestaurantIdAndNameContainingIgnoreCaseAndAvailableTrue(UUID restaurantId, String name, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from FoodItem item where item.id = :id")
    Optional<FoodItem> findByIdForUpdate(@Param("id") UUID id);
}
