package com.foodflow.delivery.service;

import com.foodflow.delivery.client.OrderClient;
import com.foodflow.delivery.entity.Delivery;
import com.foodflow.delivery.dto.DeliveryResponse;
import com.foodflow.delivery.repository.DeliveryRepository;
import com.foodflow.delivery.repository.PartnerAvailabilityRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class DeliveryServiceTest {
  @Test
  void availableDeliveryDoesNotExposeCustomerAddressUntilClaimed() {
    UUID partnerId = UUID.randomUUID();
    Delivery available = new Delivery();
    available.setPartnerId(new UUID(0, 0));
    available.setStatus("AVAILABLE");
    available.setDeliveryAddress("Private customer address");

    assertEquals(null, DeliveryResponse.forPartner(available, partnerId).deliveryAddress());

    available.setPartnerId(partnerId);
    available.setStatus("ASSIGNED");
    assertEquals("Private customer address", DeliveryResponse.forPartner(available, partnerId).deliveryAddress());
  }

  @Test
  void partnerCanSaveAvailabilityAndNewPartnersDefaultToOnline() {
    PartnerAvailabilityRepository repository = mock(PartnerAvailabilityRepository.class);
    UUID partnerId = UUID.randomUUID();
    when(repository.findById(partnerId)).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    PartnerAvailabilityService service = new PartnerAvailabilityService(repository);

    var online = service.get(partnerId);
    var offline = service.set(partnerId, false);

    assertEquals(true, online.isAvailable());
    assertEquals(false, offline.isAvailable());
  }

  @Test
  void createsAvailableDeliveryForReadyOrder() {
    DeliveryRepository repo = mock(DeliveryRepository.class);
    OrderClient client = mock(OrderClient.class);
    UUID order = UUID.randomUUID();
    when(client.check(order)).thenReturn(Map.of(
        "orderId", order, "status", "READY_FOR_PICKUP", "deliveryAddress", "10 Main Street"));
    when(repo.findByOrderId(order)).thenReturn(Optional.empty());
    when(repo.save(any(Delivery.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Delivery delivery = new DeliveryService(repo, client).createReadyDelivery(order);

    assertEquals("AVAILABLE", delivery.getStatus());
    assertEquals(new UUID(0, 0), delivery.getPartnerId());
    assertEquals("10 Main Street", delivery.getDeliveryAddress());
  }

  @Test
  void backfillsReadyOrdersWhenPartnerLoadsQueue() {
    DeliveryRepository repo = mock(DeliveryRepository.class);
    OrderClient client = mock(OrderClient.class);
    UUID order = UUID.randomUUID();
    when(client.readyForDelivery()).thenReturn(List.of(order));
    when(client.check(order)).thenReturn(Map.of("orderId", order, "status", "READY_FOR_PICKUP"));
    when(repo.findByOrderId(order)).thenReturn(Optional.empty());
    when(repo.save(any(Delivery.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(repo.findByPartnerIdOrStatusOrderByAssignedAtDesc(any(UUID.class), eq("AVAILABLE")))
        .thenReturn(List.of());

    new DeliveryService(repo, client).mine(UUID.randomUUID());

    verify(repo).save(argThat(delivery ->
        order.equals(delivery.getOrderId()) && "AVAILABLE".equals(delivery.getStatus())));
  }

  @Test
  void rejectsDeliveryClaimedByAnotherPartner() {
    DeliveryRepository repo = mock(DeliveryRepository.class);
    Delivery available = new Delivery();
    available.setStatus("ASSIGNED");
    when(repo.findByIdForUpdate(any(UUID.class))).thenReturn(Optional.of(available));

    assertThrows(ResponseStatusException.class,
        () -> new DeliveryService(repo, mock(OrderClient.class)).claim(UUID.randomUUID(), UUID.randomUUID()));
  }

  @Test
  void codDeliveryCannotCompleteBeforePartnerConfirmsCashCollection() {
    DeliveryRepository repo = mock(DeliveryRepository.class);
    OrderClient client = mock(OrderClient.class);
    UUID deliveryId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
    Delivery delivery = new Delivery();
    delivery.setOrderId(orderId);
    delivery.setPartnerId(partnerId);
    delivery.setPaymentMethod("COD");
    delivery.setStatus("PICKED_UP");
    when(repo.findByIdForUpdate(deliveryId)).thenReturn(Optional.of(delivery));

    assertThrows(ResponseStatusException.class,
        () -> new DeliveryService(repo, client).update(deliveryId, partnerId, "DELIVERED", false));

    verify(client, never()).update(any(UUID.class), eq("DELIVERED"), eq(false));
  }

  @Test
  void codDeliveryCompletesAfterCashCollectionIsConfirmed() {
    DeliveryRepository repo = mock(DeliveryRepository.class);
    OrderClient client = mock(OrderClient.class);
    UUID deliveryId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
    Delivery delivery = new Delivery();
    delivery.setOrderId(orderId);
    delivery.setPartnerId(partnerId);
    delivery.setPaymentMethod("COD");
    delivery.setStatus("PICKED_UP");
    when(repo.findByIdForUpdate(deliveryId)).thenReturn(Optional.of(delivery));
    when(repo.save(any(Delivery.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Delivery result = new DeliveryService(repo, client).update(deliveryId, partnerId, "DELIVERED", true);

    assertEquals("DELIVERED", result.getStatus());
    org.junit.jupiter.api.Assertions.assertNotNull(result.getCashCollectedAt());
    verify(client).update(orderId, "DELIVERED", true);
  }
}
