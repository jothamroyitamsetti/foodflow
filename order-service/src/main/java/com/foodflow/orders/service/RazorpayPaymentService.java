package com.foodflow.orders.service;

import com.foodflow.orders.dto.CreatePaymentResponse;
import com.foodflow.orders.dto.VerifyPaymentRequest;
import com.foodflow.orders.dto.RefundResponse;
import com.foodflow.orders.entity.Order;
import com.foodflow.orders.entity.Payment;
import com.foodflow.orders.repository.OrderRepository;
import com.foodflow.orders.repository.PaymentRepository;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RazorpayPaymentService {
  private final RestClient.Builder restClientBuilder;
  private final OrderRepository orders;
  private final PaymentRepository payments;

  @Value("${foodflow.razorpay.key-id:}")
  private String keyId;

  @Value("${foodflow.razorpay.key-secret:}")
  private String keySecret;

  @Value("${foodflow.razorpay.api-url:https://api.razorpay.com}")
  private String apiUrl;

  @Transactional
  public CreatePaymentResponse create(UUID orderId, UUID customerId) {
    Order order = ownedOrder(orderId, customerId);
    if (!"RAZORPAY".equals(order.getPaymentMethod())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cash-on-delivery orders do not use Razorpay");
    }
    if (!"PENDING_PAYMENT".equals(order.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not awaiting payment");
    }
    requireCredentials();
    Payment existing = payments.findByOrderId(orderId).orElse(null);
    if (existing != null && "CREATED".equals(existing.getStatus())) {
      return new CreatePaymentResponse(
          orderId, existing.getRazorpayOrderId(), keyId, existing.getAmount(), existing.getCurrency());
    }

    long amountInPaise;
    try {
      amountInPaise = order.getTotalAmount().movePointRight(2).longValueExact();
    } catch (ArithmeticException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order amount cannot be charged in INR paise", ex);
    }
    if (amountInPaise < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order amount must be greater than zero");
    }

    try {
      RestClient client = client();
      JsonNode response = client.post()
          .uri("/v1/orders")
          .body(Map.of(
              "amount", amountInPaise,
              "currency", "INR",
              "receipt", orderId.toString(),
              "notes", Map.of("foodflow_order_id", orderId.toString())))
          .retrieve()
          .body(JsonNode.class);
      if (response == null || response.path("id").asString("").isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay returned an invalid payment order");
      }

      Payment payment = payments.findByOrderId(orderId).orElseGet(Payment::new);
      payment.setOrderId(orderId);
      payment.setRazorpayOrderId(response.path("id").asString(""));
      payment.setRazorpayPaymentId(null);
      payment.setAmount(order.getTotalAmount());
      payment.setCurrency("INR");
      payment.setStatus("CREATED");
      payment.setUpdatedAt(Instant.now());
      payments.save(payment);

      return new CreatePaymentResponse(
          orderId, payment.getRazorpayOrderId(), keyId, payment.getAmount(), payment.getCurrency());
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create a Razorpay payment order", ex);
    }
  }

  @Transactional
  public void verify(UUID orderId, UUID customerId, VerifyPaymentRequest request) {
    Order order = ownedOrder(orderId, customerId);
    if (!"RAZORPAY".equals(order.getPaymentMethod())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cash-on-delivery orders do not use Razorpay");
    }
    Payment payment = payments.findByOrderId(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Payment order has not been created"));
    if (!payment.getRazorpayOrderId().equals(request.razorpayOrderId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment order does not match this FoodFlow order");
    }
    if ("PAID".equals(payment.getStatus())) {
      if (request.razorpayPaymentId().equals(payment.getRazorpayPaymentId())) {
        return;
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This order has already been paid");
    }
    requireCredentials();
    verifyCheckoutSignature(request);

    try {
      long expectedAmount = payment.getAmount().movePointRight(2).longValueExact();
      JsonNode providerPayment = client().get()
          .uri("/v1/payments/{paymentId}", request.razorpayPaymentId())
          .retrieve()
          .body(JsonNode.class);
      if (providerPayment == null
          || !request.razorpayOrderId().equals(providerPayment.path("order_id").asString(""))
          || providerPayment.path("amount").asLong(-1) != expectedAmount
          || !"INR".equals(providerPayment.path("currency").asString(""))) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Razorpay payment does not match this order's amount and currency");
      }
      if ("authorized".equals(providerPayment.path("status").asString(""))) {
        providerPayment = client().post()
            .uri("/v1/payments/{paymentId}/capture", request.razorpayPaymentId())
            .body(Map.of("amount", expectedAmount, "currency", "INR"))
            .retrieve()
            .body(JsonNode.class);
      }
      if (providerPayment == null
          || !request.razorpayOrderId().equals(providerPayment.path("order_id").asString(""))
          || providerPayment.path("amount").asLong(-1) != expectedAmount
          || !"INR".equals(providerPayment.path("currency").asString(""))
          || !"captured".equals(providerPayment.path("status").asString(""))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Razorpay has not confirmed a captured payment");
      }
      payment.setRazorpayPaymentId(request.razorpayPaymentId());
      payment.setStatus("PAID");
      payment.setUpdatedAt(Instant.now());
      payments.save(payment);
      order.setPaymentStatus("PAID");
      String previousStatus = order.getStatus();
      order.setStatus("PENDING");
      order.recordStatusChange(previousStatus, "PENDING", customerId);
      orders.save(order);
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not verify the Razorpay payment", ex);
    }
  }

  @Transactional
  public RefundResponse refund(UUID orderId, UUID adminId) {
    return refund(orderId, adminId, true);
  }

  @Transactional
  public RefundResponse refundForRestaurantRejection(UUID orderId, UUID ownerId) {
    return refund(orderId, ownerId, false);
  }

  private RefundResponse refund(UUID orderId, UUID initiatedBy, boolean cancelOrder) {
    Order order = orders.findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!"RAZORPAY".equals(order.getPaymentMethod())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only captured Razorpay payments can be refunded");
    }
    if (!java.util.List.of("PENDING", "ACCEPTED").contains(order.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Refund is available only before kitchen preparation starts");
    }
    Payment payment = payments.findByOrderId(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Payment record not found"));
    if ("REFUNDED".equals(payment.getStatus()) || "REFUND_PENDING".equals(payment.getStatus())) {
      return refundResponse(orderId, payment);
    }
    if (!"PAID".equals(payment.getStatus()) || payment.getRazorpayPaymentId() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a confirmed captured payment can be refunded");
    }
    requireCredentials();
    long amountInPaise;
    try {
      amountInPaise = payment.getAmount().movePointRight(2).longValueExact();
    } catch (ArithmeticException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount cannot be represented in INR paise", exception);
    }

    try {
      JsonNode response = client().post()
          .uri("/v1/payments/{paymentId}/refund", payment.getRazorpayPaymentId())
          .body(Map.of(
              "amount", amountInPaise,
              "notes", Map.of("foodflow_order_id", orderId.toString(), "initiated_by", initiatedBy.toString())))
          .retrieve()
          .body(JsonNode.class);
      if (response == null || response.path("id").asString("").isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay returned an invalid refund response");
      }
      String providerStatus = response.path("status").asString("");
      if (!java.util.List.of("processed", "pending", "queued").contains(providerStatus)) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay returned an unsupported refund status");
      }
      payment.setRazorpayRefundId(response.path("id").asString(""));
      payment.setStatus("processed".equals(providerStatus) ? "REFUNDED" : "REFUND_PENDING");
      payment.setUpdatedAt(Instant.now());
      payments.save(payment);
      order.setPaymentStatus(payment.getStatus());
      if (cancelOrder && "REFUNDED".equals(payment.getStatus())) {
        cancelUnstartedOrder(order, initiatedBy);
      }
      orders.save(order);
      return refundResponse(orderId, payment);
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not process the Razorpay refund", exception);
    }
  }

  @Transactional
  public RefundResponse refreshRefund(UUID orderId) {
    Order order = orders.findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    Payment payment = payments.findByOrderId(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment record not found"));
    if (!"REFUND_PENDING".equals(payment.getStatus()) || payment.getRazorpayRefundId() == null) {
      return refundResponse(orderId, payment);
    }
    requireCredentials();
    try {
      JsonNode response = client().get()
          .uri("/v1/refunds/{refundId}", payment.getRazorpayRefundId())
          .retrieve()
          .body(JsonNode.class);
      if (response == null) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay returned an empty refund status");
      }
      String providerStatus = response.path("status").asString("");
      if ("processed".equals(providerStatus)) {
        payment.setStatus("REFUNDED");
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);
        order.setPaymentStatus("REFUNDED");
        cancelUnstartedOrder(order, null);
        orders.save(order);
      } else if ("failed".equals(providerStatus)) {
        payment.setStatus("PAID");
        payment.setRazorpayRefundId(null);
        payment.setUpdatedAt(Instant.now());
        payments.save(payment);
        order.setPaymentStatus("PAID");
        orders.save(order);
      } else if (!java.util.List.of("pending", "queued").contains(providerStatus)) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay returned an unsupported refund status");
      }
      return refundResponse(orderId, payment);
    } catch (RestClientException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not refresh the Razorpay refund status", exception);
    }
  }

  private RefundResponse refundResponse(UUID orderId, Payment payment) {
    return new RefundResponse(orderId, payment.getRazorpayRefundId(), payment.getAmount(),
        payment.getStatus(), payment.getUpdatedAt());
  }

  private void cancelUnstartedOrder(Order order, UUID adminId) {
    if (java.util.List.of("PENDING", "ACCEPTED").contains(order.getStatus())) {
      String previousStatus = order.getStatus();
      order.setStatus("CANCELLED");
      order.recordStatusChange(previousStatus, "CANCELLED", adminId);
    }
  }

  private Order ownedOrder(UUID orderId, UUID customerId) {
    Order order = orders.findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!order.getCustomerId().equals(customerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot pay for this order");
    }
    return order;
  }

  private RestClient client() {
    return restClientBuilder.baseUrl(apiUrl)
        .defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret))
        .build();
  }

  private void requireCredentials() {
    if (keyId.isBlank() || keySecret.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Razorpay sandbox is not configured; set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
    }
  }

  private void verifyCheckoutSignature(VerifyPaymentRequest request) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expected = mac.doFinal(
          (request.razorpayOrderId() + "|" + request.razorpayPaymentId()).getBytes(StandardCharsets.UTF_8));
      byte[] supplied = HexFormat.of().parseHex(request.razorpaySignature());
      if (!MessageDigest.isEqual(expected, supplied)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay payment signature");
      }
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay payment signature", ex);
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
    }
  }
}
