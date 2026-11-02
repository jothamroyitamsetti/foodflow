package com.foodflow.delivery.repository;

import com.foodflow.delivery.entity.PartnerAvailability;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerAvailabilityRepository extends JpaRepository<PartnerAvailability, UUID> {}
