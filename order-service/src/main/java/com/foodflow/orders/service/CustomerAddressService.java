package com.foodflow.orders.service;

import com.foodflow.orders.dto.CreateAddressRequest;
import com.foodflow.orders.entity.CustomerAddress;
import com.foodflow.orders.repository.CustomerAddressRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {
  private final CustomerAddressRepository addresses;

  public List<CustomerAddress> mine(UUID customerId) {
    return addresses.findAllByCustomerIdOrderByCreatedAtDesc(customerId);
  }

  @Transactional
  public CustomerAddress create(UUID customerId, CreateAddressRequest request) {
    CustomerAddress address = new CustomerAddress();
    address.setCustomerId(customerId);
    address.setLabel(request.label().trim());
    address.setAddress(request.address().trim());
    return addresses.save(address);
  }

  @Transactional
  public void delete(UUID customerId, UUID addressId) {
    CustomerAddress address = addresses.findByIdAndCustomerId(addressId, customerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    addresses.delete(address);
  }
}
