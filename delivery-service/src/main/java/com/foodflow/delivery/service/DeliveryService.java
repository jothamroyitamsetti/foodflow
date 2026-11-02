package com.foodflow.delivery.service;

import com.foodflow.delivery.client.OrderClient;
import com.foodflow.delivery.entity.Delivery;
import com.foodflow.delivery.repository.DeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DeliveryService {
  private static final UUID UNASSIGNED_PARTNER_ID = new UUID(0, 0);

  private final DeliveryRepository repo;
  private final OrderClient orders;

  public Delivery assign(UUID orderId, UUID partnerId) {
    Map<String, Object> order = orders.check(orderId);
    Delivery existing = repo.findByOrderId(orderId).orElse(null);
    if (existing != null) {
      if ("AVAILABLE".equals(existing.getStatus())) {
        return claim(existing.getId(), partnerId);
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery already assigned");
    }
    Delivery delivery = new Delivery();
    delivery.setOrderId(orderId);
    delivery.setPartnerId(partnerId);
    delivery.setPaymentMethod(String.valueOf(order.getOrDefault("paymentMethod", "RAZORPAY")));
    delivery.setDeliveryAddress((String) order.get("deliveryAddress"));
    return repo.save(delivery);
  }

  public Delivery createReadyDelivery(UUID orderId) {
    Map<String, Object> order = orders.check(orderId);
    if (!"READY_FOR_PICKUP".equals(order.get("status"))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not ready for pickup");
    }
    return repo.findByOrderId(orderId).orElseGet(() -> {
      Delivery delivery = new Delivery();
      delivery.setOrderId(orderId);
      delivery.setPartnerId(UNASSIGNED_PARTNER_ID);
      delivery.setStatus("AVAILABLE");
      delivery.setPaymentMethod(String.valueOf(order.getOrDefault("paymentMethod", "RAZORPAY")));
      delivery.setDeliveryAddress((String) order.get("deliveryAddress"));
      return repo.save(delivery);
    });
  }

  public List<Delivery> mine(UUID partnerId) {
    for (UUID orderId : orders.readyForDelivery()) {
      createReadyDelivery(orderId);
    }
    return repo.findByPartnerIdOrStatusOrderByAssignedAtDesc(partnerId, "AVAILABLE");
  }

  @Transactional
  public Delivery claim(UUID id, UUID partnerId) {
    Delivery delivery = repo.findByIdForUpdate(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found"));
    if (!"AVAILABLE".equals(delivery.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery is no longer available");
    }
    delivery.setPartnerId(partnerId);
    delivery.setStatus("ASSIGNED");
    delivery.setAssignedAt(Instant.now());
    return repo.save(delivery);
  }

  @Transactional
  public Delivery update(UUID id, UUID partnerId, String status, boolean cashCollected) {
    Delivery delivery = repo.findByIdForUpdate(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found"));
    if (!delivery.getPartnerId().equals(partnerId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not assigned to this delivery");
    }
    List<String> allowed = "ASSIGNED".equals(delivery.getStatus())
        ? List.of("PICKED_UP")
        : "PICKED_UP".equals(delivery.getStatus()) ? List.of("DELIVERED") : List.of();
    if (!allowed.contains(status)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid delivery transition");
    }
    if("DELIVERED".equals(status)&&"COD".equals(delivery.getPaymentMethod())&&!cashCollected) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirm cash collection before completing a COD delivery");
    }
    if ("DELIVERED".equals(status) && "COD".equals(delivery.getPaymentMethod())) {
      delivery.setCashCollectedAt(Instant.now());
    }
    delivery.setStatus(status);
    Delivery saved = repo.save(delivery);
    orders.update(delivery.getOrderId(), "PICKED_UP".equals(status) ? "OUT_FOR_DELIVERY" : "DELIVERED",
        "DELIVERED".equals(status)&&cashCollected);
    return saved;
  }

  public Delivery update(UUID id, UUID partnerId, String status) {
    return update(id, partnerId, status, false);
  }
}
