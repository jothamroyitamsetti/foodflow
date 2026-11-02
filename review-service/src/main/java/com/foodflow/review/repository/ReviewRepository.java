package com.foodflow.review.repository;

import com.foodflow.review.entity.Review;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
  Page<Review> findByRestaurantId(UUID restaurantId, Pageable pageable);

  boolean existsByOrderIdAndCustomerId(UUID orderId, UUID customerId);

  Optional<Review> findByOrderIdAndCustomerId(UUID orderId, UUID customerId);
}
