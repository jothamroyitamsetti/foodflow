package com.foodflow.orders.service;

import com.foodflow.orders.entity.Order;
import com.foodflow.orders.entity.Payment;
import com.foodflow.orders.repository.OrderRepository;
import com.foodflow.orders.repository.PaymentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RazorpayPaymentServiceTest {
  @Test
  void reusesCreatedProviderOrderForPaymentRetries() {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setCustomerId(customerId);
    order.setTotalAmount(new BigDecimal("125.00"));
    Payment payment = new Payment();
    payment.setOrderId(orderId);
    payment.setRazorpayOrderId("order_provider_123");
    payment.setAmount(order.getTotalAmount());
    payment.setCurrency("INR");
    payment.setStatus("CREATED");

    OrderRepository orders = mock(OrderRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    RestClient.Builder restClientBuilder = mock(RestClient.Builder.class);
    when(orders.findById(orderId)).thenReturn(Optional.of(order));
    when(payments.findByOrderId(orderId)).thenReturn(Optional.of(payment));
    RazorpayPaymentService service = new RazorpayPaymentService(restClientBuilder, orders, payments);
    ReflectionTestUtils.setField(service, "keyId", "rzp_test_key");
    ReflectionTestUtils.setField(service, "keySecret", "test_secret");

    var response = service.create(orderId, customerId);

    assertEquals("order_provider_123", response.razorpayOrderId());
    assertEquals("rzp_test_key", response.keyId());
    assertEquals(new BigDecimal("125.00"), response.amount());
    verify(restClientBuilder, never()).baseUrl(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void rejectsPaymentAttemptByAnotherCustomer() {
    UUID orderId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setCustomerId(ownerId);
    OrderRepository orders = mock(OrderRepository.class);
    when(orders.findById(orderId)).thenReturn(Optional.of(order));
    RazorpayPaymentService service = new RazorpayPaymentService(
        mock(RestClient.Builder.class), orders, mock(PaymentRepository.class));

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class, () -> service.create(orderId, UUID.randomUUID()));

    assertEquals(403, error.getStatusCode().value());
  }

  @Test
  void capturesAuthorizedRazorpayPaymentBeforeConfirmingOrder() throws Exception {
    UUID orderId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setCustomerId(customerId);
    order.setStatus("PENDING_PAYMENT");
    Payment payment = new Payment();
    payment.setOrderId(orderId);
    payment.setRazorpayOrderId("order_provider_123");
    payment.setAmount(new BigDecimal("125.00"));
    payment.setCurrency("INR");
    payment.setStatus("CREATED");

    String keySecret = "test_secret";
    String paymentId = "pay_provider_123";
    String signature = signature("order_provider_123", paymentId, keySecret);
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    server.expect(requestTo("/v1/payments/" + paymentId))
        .andExpect(method(org.springframework.http.HttpMethod.GET))
        .andRespond(withSuccess(
            "{\"id\":\"pay_provider_123\",\"order_id\":\"order_provider_123\",\"amount\":12500,"
                + "\"currency\":\"INR\",\"status\":\"authorized\"}",
            MediaType.APPLICATION_JSON));
    server.expect(requestTo("/v1/payments/" + paymentId + "/capture"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andExpect(content().json("{\"amount\":12500,\"currency\":\"INR\"}"))
        .andRespond(withSuccess(
            "{\"id\":\"pay_provider_123\",\"order_id\":\"order_provider_123\",\"amount\":12500,"
                + "\"currency\":\"INR\",\"status\":\"captured\"}",
            MediaType.APPLICATION_JSON));

    OrderRepository orders = mock(OrderRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    when(orders.findById(orderId)).thenReturn(Optional.of(order));
    when(payments.findByOrderId(orderId)).thenReturn(Optional.of(payment));
    when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    RazorpayPaymentService service = new RazorpayPaymentService(restClientBuilder, orders, payments);
    ReflectionTestUtils.setField(service, "keyId", "rzp_test_key");
    ReflectionTestUtils.setField(service, "keySecret", keySecret);

    assertDoesNotThrow(() -> service.verify(
        orderId,
        customerId,
        new com.foodflow.orders.dto.VerifyPaymentRequest("order_provider_123", paymentId, signature)));

    server.verify();
    assertEquals("PAID", payment.getStatus());
    assertEquals(paymentId, payment.getRazorpayPaymentId());
    assertEquals("PENDING", order.getStatus());
    verify(payments).save(payment);
    verify(orders).save(order);
  }

  @Test
  void adminCanRefundCapturedPaymentAndCancelUnstartedOrder() {
    UUID orderId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setStatus("PENDING");
    order.setPaymentMethod("RAZORPAY");
    order.setPaymentStatus("PAID");
    Payment payment = new Payment();
    payment.setOrderId(orderId);
    payment.setRazorpayPaymentId("pay_captured");
    payment.setAmount(new BigDecimal("125.00"));
    payment.setStatus("PAID");

    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    server.expect(requestTo("/v1/payments/pay_captured/refund"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andExpect(content().json("{\"amount\":12500}"))
        .andRespond(withSuccess(
            "{\"id\":\"rfnd_123\",\"payment_id\":\"pay_captured\",\"status\":\"processed\"}",
            MediaType.APPLICATION_JSON));
    OrderRepository orders = mock(OrderRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    when(orders.findById(orderId)).thenReturn(Optional.of(order));
    when(payments.findByOrderId(orderId)).thenReturn(Optional.of(payment));
    when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    RazorpayPaymentService service = new RazorpayPaymentService(restClientBuilder, orders, payments);
    ReflectionTestUtils.setField(service, "keyId", "rzp_test_key");
    ReflectionTestUtils.setField(service, "keySecret", "test_secret");

    var result = service.refund(orderId, adminId);

    server.verify();
    assertEquals("rfnd_123", result.refundId());
    assertEquals("REFUNDED", result.status());
    assertEquals("CANCELLED", order.getStatus());
    assertEquals("REFUNDED", order.getPaymentStatus());
    assertEquals("CANCELLED", order.getStatusHistory().get(0).getStatus());
  }

  @Test
  void keepsPendingRefundUncancelledUntilProviderConfirmsIt() {
    UUID orderId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setStatus("ACCEPTED");
    order.setPaymentMethod("RAZORPAY");
    order.setPaymentStatus("PAID");
    Payment payment = new Payment();
    payment.setOrderId(orderId);
    payment.setRazorpayPaymentId("pay_captured");
    payment.setAmount(new BigDecimal("125.00"));
    payment.setStatus("PAID");

    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    server.expect(requestTo("/v1/payments/pay_captured/refund"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withSuccess(
            "{\"id\":\"rfnd_456\",\"payment_id\":\"pay_captured\",\"status\":\"pending\"}",
            MediaType.APPLICATION_JSON));
    server.expect(requestTo("/v1/refunds/rfnd_456"))
        .andExpect(method(org.springframework.http.HttpMethod.GET))
        .andRespond(withSuccess(
            "{\"id\":\"rfnd_456\",\"payment_id\":\"pay_captured\",\"status\":\"processed\"}",
            MediaType.APPLICATION_JSON));
    OrderRepository orders = mock(OrderRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    when(orders.findById(orderId)).thenReturn(Optional.of(order));
    when(payments.findByOrderId(orderId)).thenReturn(Optional.of(payment));
    when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    RazorpayPaymentService service = new RazorpayPaymentService(restClientBuilder, orders, payments);
    ReflectionTestUtils.setField(service, "keyId", "rzp_test_key");
    ReflectionTestUtils.setField(service, "keySecret", "test_secret");

    var pending = service.refund(orderId, UUID.randomUUID());
    assertEquals("REFUND_PENDING", pending.status());
    assertEquals("ACCEPTED", order.getStatus());

    var processed = service.refreshRefund(orderId);
    server.verify();
    assertEquals("REFUNDED", processed.status());
    assertEquals("CANCELLED", order.getStatus());
    assertEquals("REFUNDED", order.getPaymentStatus());
  }

  @Test
  void rejectsCashOnDeliveryRefundWithoutCallingProvider() {
    UUID orderId = UUID.randomUUID();
    Order order = new Order();
    order.setId(orderId);
    order.setStatus("PENDING");
    order.setPaymentMethod("COD");
    OrderRepository orders = mock(OrderRepository.class);
    RestClient.Builder builder = mock(RestClient.Builder.class);
    when(orders.findById(orderId)).thenReturn(Optional.of(order));
    RazorpayPaymentService service = new RazorpayPaymentService(builder, orders, mock(PaymentRepository.class));

    assertThrows(ResponseStatusException.class, () -> service.refund(orderId, UUID.randomUUID()));

    verify(builder, never()).baseUrl(org.mockito.ArgumentMatchers.anyString());
  }

  private static String signature(String orderId, String paymentId, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
  }
}
