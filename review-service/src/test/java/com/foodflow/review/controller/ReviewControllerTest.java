package com.foodflow.review.controller;

import com.foodflow.review.client.OrderClient;
import com.foodflow.review.dto.ReviewRequest;
import com.foodflow.review.entity.Review;
import com.foodflow.review.repository.ReviewRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewControllerTest {
  @Test
  void reviewIsRejectedBeforeOrderIsDelivered() {
    UUID customer = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    ReviewController controller = controllerFor(customer, orderId, "PREPARING", UUID.randomUUID());

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> controller.create(authentication(customer), new ReviewRequest(orderId, UUID.randomUUID(), 5, "Good")));

    assertEquals(409, exception.getStatusCode().value());
  }

  @Test
  void customerCanReviewTheirDeliveredOrderOnce() {
    UUID customer = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    ReviewRepository reviews = mock(ReviewRepository.class);
    OrderClient orders = orderClient(customer, orderId, "DELIVERED", restaurantId);
    when(reviews.saveAndFlush(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
    ReviewController controller = new ReviewController(reviews, orders);

    Review result = controller.create(
        authentication(customer), new ReviewRequest(orderId, restaurantId, 5, "Arrived hot"));

    assertEquals(orderId, result.getOrderId());
    assertEquals(restaurantId, result.getRestaurantId());
    assertEquals(5, result.getRating());
    verify(reviews).saveAndFlush(any(Review.class));
  }

  @Test
  void customerCannotReviewAnotherCustomersOrder() {
    UUID customer = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    ReviewController controller = controllerFor(
        UUID.randomUUID(), orderId, "DELIVERED", UUID.randomUUID());

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> controller.create(authentication(customer), new ReviewRequest(orderId, UUID.randomUUID(), 4, "")));

    assertEquals(403, exception.getStatusCode().value());
  }

  @Test
  void customerCannotAttachReviewToDifferentRestaurant() {
    UUID customer = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    ReviewController controller = controllerFor(customer, orderId, "DELIVERED", UUID.randomUUID());

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> controller.create(authentication(customer), new ReviewRequest(orderId, UUID.randomUUID(), 4, "")));

    assertEquals(400, exception.getStatusCode().value());
  }

  @Test
  void existingReviewIsReportedForTheCustomerOrder() {
    UUID customer = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    ReviewRepository reviews = mock(ReviewRepository.class);
    when(reviews.existsByOrderIdAndCustomerId(orderId, customer)).thenReturn(true);
    ReviewController controller = new ReviewController(
        reviews, orderClient(customer, orderId, "DELIVERED", restaurantId));

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> controller.create(authentication(customer), new ReviewRequest(orderId, restaurantId, 4, "")));

    assertEquals(409, exception.getStatusCode().value());
  }

  private ReviewController controllerFor(
      UUID customer, UUID orderId, String status, UUID restaurantId) {
    return new ReviewController(
        mock(ReviewRepository.class), orderClient(customer, orderId, status, restaurantId));
  }

  private OrderClient orderClient(UUID customer, UUID orderId, String status, UUID restaurantId) {
    OrderClient orders = mock(OrderClient.class);
    when(orders.reviewCheck(orderId)).thenReturn(Map.of(
        "customerId", customer.toString(),
        "restaurantId", restaurantId.toString(),
        "status", status));
    return orders;
  }

  private Authentication authentication(UUID customer) {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn(customer.toString());
    return authentication;
  }
}
