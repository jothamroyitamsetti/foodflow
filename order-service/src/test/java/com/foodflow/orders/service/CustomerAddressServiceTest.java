package com.foodflow.orders.service;

import com.foodflow.orders.repository.CustomerAddressRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerAddressServiceTest {
  @Test
  void customerCannotDeleteAnotherCustomersAddress() {
    CustomerAddressRepository repository = mock(CustomerAddressRepository.class);
    UUID addressId = UUID.randomUUID();
    UUID otherCustomerId = UUID.randomUUID();
    when(repository.findByIdAndCustomerId(addressId, otherCustomerId)).thenReturn(Optional.empty());
    CustomerAddressService service = new CustomerAddressService(repository);

    assertThrows(ResponseStatusException.class,
        () -> service.delete(otherCustomerId, addressId));
    verify(repository, never()).delete(org.mockito.ArgumentMatchers.any());
  }
}
