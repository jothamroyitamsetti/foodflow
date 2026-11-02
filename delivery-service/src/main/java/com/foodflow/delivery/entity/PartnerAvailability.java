package com.foodflow.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "delivery_partner_availability")
@Getter
@Setter
@NoArgsConstructor
public class PartnerAvailability {
  @Id
  @Column(name = "partner_id")
  private UUID partnerId;

  @Column(nullable = false)
  private boolean available = true;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
}
