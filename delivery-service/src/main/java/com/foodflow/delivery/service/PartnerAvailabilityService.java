package com.foodflow.delivery.service;

import com.foodflow.delivery.entity.PartnerAvailability;
import com.foodflow.delivery.repository.PartnerAvailabilityRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartnerAvailabilityService {
  private final PartnerAvailabilityRepository repository;

  @Transactional
  public PartnerAvailability get(UUID partnerId) {
    return repository.findById(partnerId).orElseGet(() -> createDefault(partnerId));
  }

  @Transactional
  public PartnerAvailability set(UUID partnerId, boolean available) {
    PartnerAvailability profile = repository.findById(partnerId)
        .orElseGet(() -> createDefault(partnerId));
    profile.setAvailable(available);
    profile.setUpdatedAt(Instant.now());
    return repository.save(profile);
  }

  public boolean isAvailable(UUID partnerId) {
    return get(partnerId).isAvailable();
  }

  private PartnerAvailability createDefault(UUID partnerId) {
    PartnerAvailability profile = new PartnerAvailability();
    profile.setPartnerId(partnerId);
    return repository.save(profile);
  }
}
