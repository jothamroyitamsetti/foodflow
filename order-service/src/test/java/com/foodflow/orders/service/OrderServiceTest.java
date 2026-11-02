package com.foodflow.orders.service;

import com.foodflow.orders.client.CatalogClient;
import com.foodflow.orders.client.CouponClient;
import com.foodflow.orders.entity.Order;
import com.foodflow.orders.dto.CatalogItemResponse;
import com.foodflow.orders.dto.CreateOrderRequest;
import com.foodflow.orders.repository.OrderRepository;
import com.foodflow.orders.repository.PaymentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class OrderServiceTest {
  @Test
  void customerCanCancelAnUnpaidOrderAndReservedStockIsReleased() {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    Order.OrderLine line = new Order.OrderLine();
    line.setMenuItemId(itemId);
    line.setQuantity(2);
    Order order = new Order();
    order.setId(orderId);
    order.setCustomerId(customerId);
    order.setPaymentMethod("COD");
    order.setPaymentStatus("CASH_DUE");
    order.setStatus("PENDING");
    order.setItems(java.util.List.of(line));
    OrderRepository repo = mock(OrderRepository.class);
    CatalogClient catalog = mock(CatalogClient.class);
    when(repo.findById(orderId)).thenReturn(Optional.of(order));
    when(repo.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    OrderService service = new OrderService(
        repo, catalog, mock(CouponClient.class), mock(PaymentRepository.class));

    Order cancelled = service.cancel(orderId, customerId);

    assertEquals("CANCELLED", cancelled.getStatus());
    assertEquals("CANCELLED", cancelled.getStatusHistory().get(0).getStatus());
    verify(catalog).release(eq(itemId), org.mockito.ArgumentMatchers.argThat(stock -> stock.quantity() == 2));
  }

  @Test
  void customerCannotCancelPaidOrderWithoutRefund() {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setCustomerId(customerId);
    order.setPaymentStatus("PAID");
    order.setStatus("PENDING");
    OrderRepository repo = mock(OrderRepository.class);
    CatalogClient catalog = mock(CatalogClient.class);
    when(repo.findById(orderId)).thenReturn(Optional.of(order));
    OrderService service = new OrderService(
        repo, catalog, mock(CouponClient.class), mock(PaymentRepository.class));

    assertThrows(ResponseStatusException.class, () -> service.cancel(orderId, customerId));
    verify(catalog, never()).release(any(UUID.class), any());
  }

  @Test
  void missingOrderIsNotFound() {
    OrderRepository repo = mock(OrderRepository.class);
    when(repo.findById(any(UUID.class))).thenReturn(Optional.empty());
    OrderService service = new OrderService(
        repo, mock(CatalogClient.class), mock(CouponClient.class), mock(PaymentRepository.class));

    assertThrows(ResponseStatusException.class,
        () -> service.get(UUID.randomUUID(), UUID.randomUUID(), false));
  }

  @Test
  void deliveryCannotCompleteBeforePaymentIsConfirmed() {
    UUID id = UUID.randomUUID();
    Order order = new Order();
    order.setId(id);
    order.setStatus("OUT_FOR_DELIVERY");
    OrderRepository repo = mock(OrderRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    when(repo.findById(id)).thenReturn(Optional.of(order));
    when(payments.existsByOrderIdAndStatus(id, "PAID")).thenReturn(false);
    OrderService service = new OrderService(repo, mock(CatalogClient.class), mock(CouponClient.class), payments);

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class, () -> service.updateStatus(id, "DELIVERED"));

    assertEquals(409, error.getStatusCode().value());
  }

  @Test
  void deliveryCanCompleteAfterPaymentIsConfirmed() {
    UUID id = UUID.randomUUID();
    Order order = new Order();
    order.setId(id);
    order.setStatus("OUT_FOR_DELIVERY");
    OrderRepository repo = mock(OrderRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    when(repo.findById(id)).thenReturn(Optional.of(order));
    when(payments.existsByOrderIdAndStatus(id, "PAID")).thenReturn(true);
    when(repo.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    OrderService service = new OrderService(repo, mock(CatalogClient.class), mock(CouponClient.class), payments);

    assertEquals("DELIVERED", service.updateStatus(id, "DELIVERED").getStatus());
  }

  @Test
  void cashOnDeliveryStartsWithoutPrepaymentAndNeedsCashCollectionToFinish() {
    UUID id = UUID.randomUUID();
    Order order = new Order();
    order.setId(id);
    order.setPaymentMethod("COD");
    order.setStatus("OUT_FOR_DELIVERY");
    OrderRepository repo = mock(OrderRepository.class);
    when(repo.findById(id)).thenReturn(Optional.of(order));
    when(repo.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    OrderService service = new OrderService(repo, mock(CatalogClient.class), mock(CouponClient.class),
        mock(PaymentRepository.class));

    assertThrows(ResponseStatusException.class, () -> service.updateStatus(id, "DELIVERED"));
    Order delivered = service.updateStatus(id, "DELIVERED", true);

    assertEquals("DELIVERED", delivered.getStatus());
    assertEquals(1, delivered.getStatusHistory().size());
    assertEquals("DELIVERED", delivered.getStatusHistory().get(0).getStatus());
    assertEquals("CASH_COLLECTED", delivered.getPaymentStatus());
  }

  @Test
  void restaurantCanRejectCashOrderAndReservedStockIsReleased() {
    UUID orderId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    Order.OrderLine line = new Order.OrderLine();
    line.setMenuItemId(itemId);
    line.setQuantity(2);
    Order order = new Order();
    order.setId(orderId);
    order.setRestaurantId(restaurantId);
    order.setPaymentMethod("COD");
    order.setPaymentStatus("CASH_DUE");
    order.setStatus("PENDING");
    order.setItems(java.util.List.of(line));
    OrderRepository repo = mock(OrderRepository.class);
    CatalogClient catalog = mock(CatalogClient.class);
    when(repo.findById(orderId)).thenReturn(Optional.of(order));
    when(catalog.ownerRestaurant()).thenReturn(java.util.Map.of("id", restaurantId.toString()));
    when(repo.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    OrderService service = new OrderService(
        repo, catalog, mock(CouponClient.class), mock(PaymentRepository.class));

    Order rejected = service.rejectRestaurantOrder(orderId, ownerId);

    assertEquals("REJECTED", rejected.getStatus());
    assertEquals("REJECTED", rejected.getStatusHistory().get(0).getStatus());
    verify(catalog).release(eq(itemId), org.mockito.ArgumentMatchers.argThat(stock -> stock.quantity() == 2));
  }

  @Test
  void restaurantCannotRejectPaidRazorpayOrderUntilRefundIsStarted() {
    UUID orderId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setRestaurantId(restaurantId);
    order.setPaymentMethod("RAZORPAY");
    order.setPaymentStatus("PAID");
    order.setStatus("PENDING");
    OrderRepository repo = mock(OrderRepository.class);
    CatalogClient catalog = mock(CatalogClient.class);
    when(repo.findById(orderId)).thenReturn(Optional.of(order));
    when(catalog.ownerRestaurant()).thenReturn(java.util.Map.of("id", restaurantId.toString()));
    OrderService service = new OrderService(
        repo, catalog, mock(CouponClient.class), mock(PaymentRepository.class));

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class, () -> service.rejectRestaurantOrder(orderId, UUID.randomUUID()));

    assertEquals(409, error.getStatusCode().value());
    verify(catalog, never()).release(any(UUID.class), any());
  }

  @Test
  void restaurantCannotRejectAnotherRestaurantsOrder() {
    UUID orderId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setRestaurantId(UUID.randomUUID());
    OrderRepository repo = mock(OrderRepository.class);
    CatalogClient catalog = mock(CatalogClient.class);
    when(repo.findById(orderId)).thenReturn(Optional.of(order));
    when(catalog.ownerRestaurant()).thenReturn(java.util.Map.of("id", UUID.randomUUID().toString()));
    OrderService service = new OrderService(
        repo, catalog, mock(CouponClient.class), mock(PaymentRepository.class));

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class, () -> service.restaurantOrderForRejection(orderId));

    assertEquals(403, error.getStatusCode().value());
  }

  @Test
  void createsCodOrderWithoutCreatingRazorpayPayment() {
    UUID itemId = UUID.randomUUID();
    UUID restaurantId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    CatalogClient catalog = mock(CatalogClient.class);
    when(catalog.reserve(eq(itemId), any())).thenReturn(new CatalogItemResponse(
        itemId, "Kitchen", "Meal", "", java.math.BigDecimal.valueOf(12), 3, true, restaurantId));
    OrderRepository repo = mock(OrderRepository.class);
    when(repo.findByCustomerIdAndIdempotencyKey(customerId, "request-1")).thenReturn(Optional.empty());
    when(repo.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    CreateOrderRequest request = new CreateOrderRequest();
    CreateOrderRequest.OrderLineRequest line = new CreateOrderRequest.OrderLineRequest();
    line.setMenuItemId(itemId);
    line.setQuantity(1);
    request.setItems(java.util.List.of(line));
    request.setPaymentMethod("COD");
    request.setDeliveryAddress("10 Main Street");
    OrderService service = new OrderService(repo, catalog, mock(CouponClient.class), mock(PaymentRepository.class));

    Order order = service.create(customerId, "request-1", request);

    assertEquals("COD", order.getPaymentMethod());
    assertEquals("CASH_DUE", order.getPaymentStatus());
    assertEquals("PENDING", order.getStatus());
    assertEquals("PENDING", order.getStatusHistory().get(0).getStatus());
    verify(catalog).reserve(eq(itemId), any());
  }

  @Test
  void releasesReservedStockWhenCartContainsMultipleRestaurants() {
    UUID firstItem = UUID.randomUUID();
    UUID secondItem = UUID.randomUUID();
    CatalogClient catalog = mock(CatalogClient.class);
    when(catalog.reserve(eq(firstItem), any())).thenReturn(new CatalogItemResponse(
        firstItem, "Kitchen One", "Meal One", "", java.math.BigDecimal.TEN, 2, true, UUID.randomUUID()));
    when(catalog.reserve(eq(secondItem), any())).thenReturn(new CatalogItemResponse(
        secondItem, "Kitchen Two", "Meal Two", "", java.math.BigDecimal.TEN, 2, true, UUID.randomUUID()));
    OrderRepository repo = mock(OrderRepository.class);
    UUID customerId = UUID.randomUUID();
    when(repo.findByCustomerIdAndIdempotencyKey(customerId, "request-2")).thenReturn(Optional.empty());
    CreateOrderRequest request = new CreateOrderRequest();
    CreateOrderRequest.OrderLineRequest firstLine = new CreateOrderRequest.OrderLineRequest();
    firstLine.setMenuItemId(firstItem);
    firstLine.setQuantity(1);
    CreateOrderRequest.OrderLineRequest secondLine = new CreateOrderRequest.OrderLineRequest();
    secondLine.setMenuItemId(secondItem);
    secondLine.setQuantity(1);
    request.setItems(java.util.List.of(firstLine, secondLine));
    request.setDeliveryAddress("10 Main Street");
    OrderService service = new OrderService(repo, catalog, mock(CouponClient.class), mock(PaymentRepository.class));

    ResponseStatusException error = assertThrows(ResponseStatusException.class,
        () -> service.create(customerId, "request-2", request));

    assertEquals(400, error.getStatusCode().value());
    verify(catalog).release(eq(firstItem), any());
    verify(catalog).release(eq(secondItem), any());
  }
}
