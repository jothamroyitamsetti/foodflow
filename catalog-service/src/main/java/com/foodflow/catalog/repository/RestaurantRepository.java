package com.foodflow.catalog.repository;

import com.foodflow.catalog.entity.Restaurant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
  Optional<Restaurant> findByOwnerId(UUID ownerId);

  Optional<Restaurant> findByOwnerIdIsNullAndNameIgnoreCase(String name);

  List<Restaurant> findAllByOrderByNameAsc();
  List<Restaurant> findByStatusAndAcceptingOrdersOrderByNameAsc(String status, boolean acceptingOrders);
}
