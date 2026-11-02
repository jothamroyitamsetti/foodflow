package com.foodflow.orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(columnNames = "order_id"),
    @UniqueConstraint(columnNames = "razorpay_order_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "razorpay_order_id", nullable = false)
  private String razorpayOrderId;

  @Column(name = "razorpay_payment_id")
  private String razorpayPaymentId;

  @Column(name = "razorpay_refund_id")
  private String razorpayRefundId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(nullable = false)
  private String status = "CREATED";

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();
}
