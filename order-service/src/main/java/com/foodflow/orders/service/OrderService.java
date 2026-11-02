package com.foodflow.orders.service;

import com.foodflow.orders.client.CatalogClient;
import com.foodflow.orders.client.CouponClient;
import com.foodflow.orders.dto.CatalogItemResponse;
import com.foodflow.orders.dto.CreateOrderRequest;
import com.foodflow.orders.dto.StockRequest;
import com.foodflow.orders.entity.Order;
import com.foodflow.orders.repository.OrderRepository;
import com.foodflow.orders.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
  private final OrderRepository repo;
  private final CatalogClient catalog;
  private final CouponClient coupons;
  private final PaymentRepository payments;

  @Transactional
  public Order create(UUID customerId, String key, CreateOrderRequest request) {
    if (key == null || key.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
    }
    Optional<Order> existing = repo.findByCustomerIdAndIdempotencyKey(customerId, key);
    if (existing.isPresent()) {
      return existing.get();
    }
    if (request.deliveryAddress() == null || request.deliveryAddress().isBlank()
        || request.deliveryAddress().length() > 500) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A delivery address of up to 500 characters is required");
    }
    if (request.couponCode() != null && !request.couponCode().isBlank()) {
      coupons.validate(request.couponCode());
    }
    String paymentMethod = request.paymentMethod();
    if (!"COD".equals(paymentMethod) && !"RAZORPAY".equals(paymentMethod)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Razorpay or cash on delivery");
    }

    List<CatalogItemResponse> reservedItems = new ArrayList<>();
    List<CreateOrderRequest.OrderLineRequest> reservedLines = new ArrayList<>();
    boolean couponRedeemed = false;
    try {
      for (CreateOrderRequest.OrderLineRequest line : request.items()) {
        CatalogItemResponse item = catalog.reserve(line.menuItemId(), new StockRequest(line.quantity()));
        reservedLines.add(line);
        reservedItems.add(item);
      }
      UUID restaurantId = reservedItems.get(0).restaurantId();
      String restaurantName = reservedItems.get(0).restaurantName();
      if (restaurantId == null) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Menu item is not linked to an approved restaurant");
      }
      for (CatalogItemResponse item : reservedItems) {
        if (!restaurantId.equals(item.restaurantId())
            || !restaurantName.equals(item.restaurantName())) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "An order can contain items from only one restaurant");
        }
      }

      Order order = new Order();
      order.setCustomerId(customerId);
      order.setIdempotencyKey(key);
      order.setRestaurantId(restaurantId);
      order.setPaymentMethod(paymentMethod);
      order.setPaymentStatus("COD".equals(paymentMethod) ? "CASH_DUE" : "UNPAID");
      order.setStatus("COD".equals(paymentMethod) ? "PENDING" : "PENDING_PAYMENT");
      order.setDeliveryAddress(request.deliveryAddress().trim());
      order.recordStatusChange(null, order.getStatus(), customerId);
      BigDecimal total = addOrderLines(order, reservedItems, request.items());

      if (request.couponCode() != null && !request.couponCode().isBlank()) {
        var coupon = coupons.redeem(request.couponCode());
        couponRedeemed = true;
        total = total.subtract(total.multiply(coupon.discountPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
      }
      order.setTotalAmount(total.max(BigDecimal.ZERO));
      return repo.saveAndFlush(order);
    } catch (RuntimeException exception) {
      releaseInventory(reservedLines);
      if (couponRedeemed && request.couponCode() != null) {
        try {
          coupons.release(request.couponCode());
        } catch (RuntimeException releaseFailure) {
          log.error("Failed to release redeemed coupon after order creation failed", releaseFailure);
          exception.addSuppressed(releaseFailure);
        }
      }
      throw exception;
    }
  }

  public Order get(UUID id, UUID customerId, boolean admin) {
    Order order = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!admin && !order.getCustomerId().equals(customerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this order");
    }
    return order;
  }

  public Page<Order> mine(UUID customerId, Pageable pageable) {
    return repo.findByCustomerId(customerId, pageable);
  }

  public Page<Order> management(Pageable pageable, boolean admin) {
    if (admin) {
      return repo.findAll(pageable);
    }
    UUID restaurantId = currentRestaurantId();
    return repo.findByRestaurantId(restaurantId, pageable);
  }

  public Order updateManagedStatus(UUID id, String status, boolean admin, UUID changedBy) {
    Order order = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!admin && !currentRestaurantId().equals(order.getRestaurantId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You can manage only orders from your restaurant");
    }
    return updateStatus(id, status, false, changedBy);
  }

  @Transactional
  public Order rejectRestaurantOrder(UUID id, UUID ownerId) {
    Order order = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!currentRestaurantId().equals(order.getRestaurantId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You can reject only orders from your restaurant");
    }
    if (!List.of("PENDING", "ACCEPTED").contains(order.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only pending or accepted orders can be rejected");
    }
    if ("RAZORPAY".equals(order.getPaymentMethod())
        && !List.of("UNPAID", "REFUNDED", "REFUND_PENDING").contains(order.getPaymentStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Refund the Razorpay payment before rejecting this order");
    }
    releaseOrderInventory(order);
    String previousStatus = order.getStatus();
    order.setStatus("REJECTED");
    order.recordStatusChange(previousStatus, "REJECTED", ownerId);
    return repo.save(order);
  }

  public Order restaurantOrderForRejection(UUID id) {
    Order order = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!currentRestaurantId().equals(order.getRestaurantId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You can reject only orders from your restaurant");
    }
    if (!List.of("PENDING", "ACCEPTED").contains(order.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only pending or accepted orders can be rejected");
    }
    return order;
  }

  @Transactional
  public Order updateStatus(UUID id, String status) {
    return updateStatus(id, status, false, null);
  }

  @Transactional
  public Order updateStatus(UUID id, String status, boolean cashCollected) {
    return updateStatus(id, status, cashCollected, null);
  }

  @Transactional
  public Order updateStatus(UUID id, String status, boolean cashCollected, UUID changedBy) {
    Order order = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    boolean fulfillment = List.of(
        "ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY", "DELIVERED")
        .contains(status);
    boolean cashOnDelivery = "COD".equals(order.getPaymentMethod());
    if (fulfillment && !cashOnDelivery
        && !payments.existsByOrderIdAndStatus(id, "PAID")) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Order must have a confirmed Razorpay payment before fulfillment");
    }
    if ("READY_FOR_PICKUP".equals(status) && status.equals(order.getStatus())) {
      return order;
    }
    Map<String, List<String>> transitions = Map.of(
        "PENDING_PAYMENT", List.of("CANCELLED"),
        "PENDING", List.of("ACCEPTED", "CANCELLED"),
        "ACCEPTED", List.of("PREPARING", "CANCELLED"),
        "PREPARING", List.of("READY_FOR_PICKUP"),
        "READY_FOR_PICKUP", List.of("OUT_FOR_DELIVERY"),
        "OUT_FOR_DELIVERY", List.of("DELIVERED"));
    if (!transitions.getOrDefault(order.getStatus(), List.of()).contains(status)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid order status transition");
    }
    if ("CANCELLED".equals(status)) {
      if ("PAID".equals(order.getPaymentStatus()) || "CASH_COLLECTED".equals(order.getPaymentStatus())) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "A paid order cannot be cancelled without completing its refund");
      }
      releaseOrderInventory(order);
    }
    if ("DELIVERED".equals(status) && cashOnDelivery) {
      if (!cashCollected) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Confirm cash collection before completing a COD delivery");
      }
      order.setPaymentStatus("CASH_COLLECTED");
    }
    String previousStatus = order.getStatus();
    order.setStatus(status);
    order.recordStatusChange(previousStatus, status, changedBy);
    return repo.save(order);
  }

  @Transactional
  public Order cancel(UUID id, UUID customerId) {
    Order order = repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!order.getCustomerId().equals(customerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel this order");
    }
    if ("CANCELLED".equals(order.getStatus())) {
      return order;
    }
    return updateStatus(id, "CANCELLED", false, customerId);
  }

  private void releaseOrderInventory(Order order) {
    try {
      for (Order.OrderLine line : order.getItems()) {
        catalog.release(line.getMenuItemId(), new StockRequest(line.getQuantity()));
      }
    } catch (RuntimeException exception) {
      log.error("Could not release inventory while cancelling order {}", order.getId(), exception);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Could not release reserved stock; order was not cancelled", exception);
    }
  }

  private BigDecimal addOrderLines(
      Order order,
      List<CatalogItemResponse> catalogItems,
      List<CreateOrderRequest.OrderLineRequest> requestLines) {
    BigDecimal total = BigDecimal.ZERO;
    for (int index = 0; index < catalogItems.size(); index++) {
      CatalogItemResponse source = catalogItems.get(index);
      CreateOrderRequest.OrderLineRequest input = requestLines.get(index);
      Order.OrderLine line = new Order.OrderLine();
      line.setMenuItemId(source.id());
      line.setItemName(source.name());
      line.setQuantity(input.quantity());
      line.setUnitPrice(source.price());
      order.getItems().add(line);
      total = total.add(source.price().multiply(BigDecimal.valueOf(input.quantity())));
    }
    return total;
  }

  private void releaseInventory(List<CreateOrderRequest.OrderLineRequest> lines) {
    for (CreateOrderRequest.OrderLineRequest line : lines) {
      try {
        catalog.release(line.menuItemId(), new StockRequest(line.quantity()));
      } catch (RuntimeException releaseFailure) {
        log.error("Failed to release inventory for menu item {}", line.menuItemId(), releaseFailure);
      }
    }
  }

  private UUID currentRestaurantId() {
    Map<String, Object> restaurant = catalog.ownerRestaurant();
    Object id = restaurant.get("id");
    if (id == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "No approved restaurant is linked to this owner");
    }
    return UUID.fromString(id.toString());
  }
}
