package com.foodflow.orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAddress {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(nullable = false, length = 80)
  private String label;

  @Column(nullable = false, length = 500)
  private String address;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
