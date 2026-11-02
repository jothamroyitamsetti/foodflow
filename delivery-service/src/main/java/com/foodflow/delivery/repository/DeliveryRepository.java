package com.foodflow.delivery.repository;

import com.foodflow.delivery.entity.Delivery;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
  List<Delivery> findByPartnerIdOrStatusOrderByAssignedAtDesc(UUID partnerId, String status);

  Optional<Delivery> findByOrderId(UUID orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Delivery d where d.id = :id")
  Optional<Delivery> findByIdForUpdate(@Param("id") UUID id);
}
