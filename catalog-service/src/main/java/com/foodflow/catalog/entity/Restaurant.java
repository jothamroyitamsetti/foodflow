package com.foodflow.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "owner_id", unique = true)
  @JsonIgnore
  private UUID ownerId;

  @Column(nullable = false, length = 20)
  private String status = "ACTIVE";

  @Column(name = "accepting_orders", nullable = false)
  private boolean acceptingOrders = true;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();
}
