package com.foodflow.orders.repository;

import com.foodflow.orders.entity.CustomerAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {
  List<CustomerAddress> findAllByCustomerIdOrderByCreatedAtDesc(UUID customerId);

  Optional<CustomerAddress> findByIdAndCustomerId(UUID id, UUID customerId);
}
