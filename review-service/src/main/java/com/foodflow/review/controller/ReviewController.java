package com.foodflow.review.controller;

import com.foodflow.review.client.OrderClient;
import com.foodflow.review.dto.ReviewRequest;
import com.foodflow.review.dto.ReviewStatusResponse;
import com.foodflow.review.entity.Review;
import com.foodflow.review.repository.ReviewRepository;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
  private final ReviewRepository repo;
  private final OrderClient orders;

  @PostMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @ResponseStatus(HttpStatus.CREATED)
  public Review create(Authentication authentication, @Valid @RequestBody ReviewRequest request) {
    UUID customerId = UUID.fromString(authentication.getName());
    Map<String, Object> order = ownedOrder(request.orderId(), customerId);
    if (!"DELIVERED".equals(String.valueOf(order.get("status")))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only delivered orders can be reviewed");
    }
    if (!request.restaurantId().toString().equals(String.valueOf(order.get("restaurantId")))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restaurant does not match the order");
    }
    if (repo.existsByOrderIdAndCustomerId(request.orderId(), customerId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this order");
    }

    Review review = new Review();
    review.setOrderId(request.orderId());
    review.setCustomerId(customerId);
    review.setRestaurantId(request.restaurantId());
    review.setRating(request.rating());
    review.setComment(request.comment());
    try {
      return repo.saveAndFlush(review);
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this order", exception);
    }
  }

  @GetMapping("/orders/{orderId}/mine")
  @PreAuthorize("hasRole('CUSTOMER')")
  public ReviewStatusResponse myOrderReview(Authentication authentication, @PathVariable UUID orderId) {
    UUID customerId = UUID.fromString(authentication.getName());
    ownedOrder(orderId, customerId);
    var review = repo.findByOrderIdAndCustomerId(orderId, customerId);
    return review.map(value -> new ReviewStatusResponse(
            orderId, true, value.getRating(), value.getComment()))
        .orElseGet(() -> new ReviewStatusResponse(orderId, false, null, null));
  }

  @GetMapping("/restaurant/{restaurantId}")
  public Page<Review> list(
      @PathVariable UUID restaurantId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return repo.findByRestaurantId(
        restaurantId,
        PageRequest.of(
            Math.max(0, page),
            Math.min(50, Math.max(1, size)),
            Sort.by(Sort.Direction.DESC, "createdAt")));
  }

  private Map<String, Object> ownedOrder(UUID orderId, UUID customerId) {
    Map<String, Object> order = orders.reviewCheck(orderId);
    if (!customerId.toString().equals(String.valueOf(order.get("customerId")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only review your own order");
    }
    return order;
  }
}
