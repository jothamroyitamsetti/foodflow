package com.foodflow.orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "restaurant_id")
  private UUID restaurantId;

  @Column(name = "payment_method", nullable = false, length = 32)
  private String paymentMethod = "RAZORPAY";

  @Column(name = "payment_status", nullable = false, length = 32)
  private String paymentStatus = "UNPAID";

  @JsonIgnore
  @Column(name = "delivery_address", length = 500)
  private String deliveryAddress;

  @Column(nullable = false)
  private String status = "PENDING_PAYMENT";

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
  private List<OrderLine> items = new ArrayList<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_status_history", joinColumns = @JoinColumn(name = "order_id"))
  @jakarta.persistence.OrderBy("changedAt ASC")
  private List<StatusChange> statusHistory = new ArrayList<>();

  public void recordStatusChange(String previousStatus, String status, UUID changedBy) {
    StatusChange change = new StatusChange();
    change.setPreviousStatus(previousStatus);
    change.setStatus(status);
    change.setChangedAt(Instant.now());
    change.setChangedBy(changedBy);
    statusHistory.add(change);
  }

  @Embeddable
  @Getter
  @Setter
  @NoArgsConstructor
  public static class StatusChange {
    @Column(name = "previous_status")
    private String previousStatus;

    @Column(nullable = false)
    private String status;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by")
    private UUID changedBy;
  }

  @Embeddable
  @Getter
  @Setter
  @NoArgsConstructor
  public static class OrderLine {
    @Column(nullable = false)
    private UUID menuItemId;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
  }
}
