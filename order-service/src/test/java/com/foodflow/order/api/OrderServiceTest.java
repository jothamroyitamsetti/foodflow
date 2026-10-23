package com.foodflow.order.api;

import com.foodflow.order.catalog.CatalogClient;
import com.foodflow.order.domain.FoodOrder;
import com.foodflow.order.domain.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final OrderService service = new OrderService(orders, catalog, "internal-token");

    @Test
    void idempotentRepeatReturnsOriginalOrderWithoutReservingAgain() {
        UUID customer = UUID.randomUUID();
        FoodOrder original = new FoodOrder();
        original.setId(UUID.randomUUID());
        original.setCustomerId(customer);
        original.setIdempotencyKey("same-request");
        original.setTotal(new BigDecimal("3.00"));
        original.setCreatedAt(Instant.now());
        when(orders.findByIdempotencyKey("same-request")).thenReturn(Optional.of(original));

        OrderDtos.OrderResponse response = service.create(customer, "same-request",
                new OrderDtos.CreateOrderRequest(List.of(new OrderDtos.LineRequest(UUID.randomUUID(), 1))));

        assertEquals(original.getId(), response.id());
        verifyNoInteractions(catalog);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void failedLaterReservationReleasesEarlierStock() {
        UUID customer = UUID.randomUUID();
        UUID restaurant = UUID.randomUUID();
        UUID firstItem = UUID.randomUUID();
        UUID secondItem = UUID.randomUUID();
        when(orders.findByIdempotencyKey("request-1")).thenReturn(Optional.empty());
        when(catalog.getItem(firstItem)).thenReturn(item(firstItem, restaurant));
        when(catalog.getItem(secondItem)).thenReturn(item(secondItem, restaurant));
        when(catalog.reserve(eq("internal-token"), argThat(request -> request.itemId().equals(firstItem))))
                .thenReturn(new CatalogClient.StockResponse(firstItem, 0));
        when(catalog.reserve(eq("internal-token"), argThat(request -> request.itemId().equals(secondItem))))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient inventory"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.create(customer,
                "request-1", new OrderDtos.CreateOrderRequest(List.of(
                        new OrderDtos.LineRequest(firstItem, 1), new OrderDtos.LineRequest(secondItem, 1)))));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(catalog).release("internal-token", new CatalogClient.StockRequest(firstItem, 1));
        verify(orders, never()).saveAndFlush(any());
    }

    private CatalogClient.FoodItemResponse item(UUID id, UUID restaurantId) {
        return new CatalogClient.FoodItemResponse(id, restaurantId, "Test dish", "", new BigDecimal("5.00"), true, 5);
    }
}
