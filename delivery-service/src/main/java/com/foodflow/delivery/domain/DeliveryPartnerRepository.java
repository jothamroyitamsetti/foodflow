package com.foodflow.delivery.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select partner from DeliveryPartner partner where partner.available = true order by partner.userId")
    List<DeliveryPartner> findAvailableForUpdate(Pageable pageable);
}
