package com.foodflow.orders.controller;

import com.foodflow.orders.dto.CreateAddressRequest;
import com.foodflow.orders.entity.CustomerAddress;
import com.foodflow.orders.service.CustomerAddressService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/addresses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerAddressController {
  private final CustomerAddressService service;

  @GetMapping
  public List<CustomerAddress> mine(Authentication authentication) {
    return service.mine(UUID.fromString(authentication.getName()));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerAddress create(
      Authentication authentication, @Valid @RequestBody CreateAddressRequest request) {
    return service.create(UUID.fromString(authentication.getName()), request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Authentication authentication, @PathVariable UUID id) {
    service.delete(UUID.fromString(authentication.getName()), id);
  }
}
