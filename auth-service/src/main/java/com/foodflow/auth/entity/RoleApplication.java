package com.foodflow.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "role_applications")
@Getter
@Setter
@NoArgsConstructor
public class RoleApplication {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String requestedRole;

  private String restaurantName;

  @Column(length = 500)
  private String rejectionReason;

  @Column(nullable = false)
  private String status = "PENDING";

  @Column(nullable = false)
  private Instant createdAt = Instant.now();
}
