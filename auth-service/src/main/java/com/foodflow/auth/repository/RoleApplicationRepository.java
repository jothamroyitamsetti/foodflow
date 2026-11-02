package com.foodflow.auth.repository;

import com.foodflow.auth.entity.RoleApplication;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleApplicationRepository extends JpaRepository<RoleApplication, UUID> {
  List<RoleApplication> findAllByOrderByCreatedAtDesc();

  List<RoleApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);
  boolean existsByUserIdAndRequestedRoleAndStatus(UUID userId, String requestedRole, String status);
}
