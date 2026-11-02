package com.foodflow.orders.dto;

import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateOrderRequestTest {
  @Test
  void cascadesValidationToOrderLinesWithoutDuplicateAnnotations() {
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    var line = new CreateOrderRequest.OrderLineRequest();
    line.setQuantity(0);
    var request = new CreateOrderRequest();
    request.setItems(List.of(line));
    request.setDeliveryAddress("10 Main Street");

    var violations = validator.validate(request);

    assertEquals(2, violations.size());
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("items[0].menuItemId")));
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("items[0].quantity")));
  }
}
