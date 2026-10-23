package com.foodflow.order.api;

import com.foodflow.order.catalog.CatalogClient;
import com.foodflow.order.domain.*;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final CatalogClient catalog;
    private final String internalToken;

    public OrderService(OrderRepository orders, CatalogClient catalog,
                        @Value("${foodflow.internal-token}") String internalToken) {
        this.orders = orders;
        this.catalog = catalog;
        this.internalToken = internalToken;
    }

    @Transactional
    public OrderDtos.OrderResponse create(UUID customerId, String idempotencyKey, OrderDtos.CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required");
        }
        Optional<FoodOrder> existing = orders.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getCustomerId().equals(customerId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already belongs to another order");
            }
            return toResponse(existing.get());
        }

        List<CatalogClient.StockRequest> reservations = new ArrayList<>();
        try {
            FoodOrder order = new FoodOrder();
            order.setCustomerId(customerId);
            order.setIdempotencyKey(idempotencyKey);
            order.setCreatedAt(Instant.now());
            order.setStatus(OrderStatus.PAID);
            BigDecimal total = BigDecimal.ZERO;
            UUID restaurantId = null;
            for (OrderDtos.LineRequest requested : request.items()) {
                CatalogClient.FoodItemResponse item = catalog.getItem(requested.itemId());
                if (restaurantId != null && !restaurantId.equals(item.restaurantId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An order can contain items from one restaurant only");
                }
                restaurantId = item.restaurantId();
                if (!item.available()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Food item is unavailable: " + item.name());
                }
                catalog.reserve(internalToken, new CatalogClient.StockRequest(item.id(), requested.quantity()));
                reservations.add(new CatalogClient.StockRequest(item.id(), requested.quantity()));
                OrderLine line = new OrderLine();
                line.setOrder(order);
                line.setItemId(item.id());
                line.setRestaurantId(item.restaurantId());
                line.setItemName(item.name());
                line.setQuantity(requested.quantity());
                line.setUnitPrice(item.price());
                order.getLines().add(line);
                total = total.add(item.price().multiply(BigDecimal.valueOf(requested.quantity())));
            }
            order.setRestaurantId(restaurantId);
            order.setTotal(total);
            addStatus(order, OrderStatus.PAID);
            return toResponse(orders.saveAndFlush(order));
        } catch (RuntimeException failure) {
            boolean compensationFailed = compensate(reservations, failure);
            if (compensationFailed) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Order creation failed and inventory compensation also failed", failure);
            }
            if (failure instanceof DataIntegrityViolationException) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order was already created for this idempotency key", failure);
            }
            if (failure instanceof ResponseStatusException) {
                throw failure;
            }
            if (failure instanceof FeignException feign && feign.status() >= 400 && feign.status() < 500) {
                throw new ResponseStatusException(HttpStatus.valueOf(feign.status()), "Catalog rejected the order request", failure);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order creation failed; reserved inventory was released", failure);
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderDtos.OrderResponse> history(UUID customerId, Pageable pageable) {
        return orders.findByCustomerId(customerId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderDtos.OrderResponse> restaurantOrders(UUID restaurantId, UUID actorId, String role, Pageable pageable) {
        if (!"ADMIN".equals(role)
                && (!"RESTAURANT_OWNER".equals(role)
                || !catalog.getRestaurant(restaurantId).ownerId().equals(actorId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not manage this restaurant's orders");
        }
        return orders.findByRestaurantId(restaurantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderResponse get(UUID id, UUID customerId, String role) {
        FoodOrder order = findOrder(id);
        if ("CUSTOMER".equals(role) && order.getCustomerId().equals(customerId)) {
            return toResponse(order);
        }
        if ("ADMIN".equals(role)) {
            return toResponse(order);
        }
        if ("RESTAURANT_OWNER".equals(role)
                && catalog.getRestaurant(order.getRestaurantId()).ownerId().equals(customerId)) {
            return toResponse(order);
        }
        if (!"CUSTOMER".equals(role) && !"RESTAURANT_OWNER".equals(role) && !"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot view another customer's order");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot view another customer's order");
    }

    @Transactional
    public OrderDtos.OrderResponse updateRestaurantStatus(UUID id, UUID actorId, String role, OrderStatus next) {
        FoodOrder order = findOrder(id);
        if ("ADMIN".equals(role)) {
            // Administrators may operate any order.
        } else if (!"RESTAURANT_OWNER".equals(role)
                || !catalog.getRestaurant(order.getRestaurantId()).ownerId().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not manage this restaurant's orders");
        }
        boolean valid = (order.getStatus() == OrderStatus.PAID && next == OrderStatus.ACCEPTED)
                || (order.getStatus() == OrderStatus.ACCEPTED && next == OrderStatus.PREPARING)
                || (order.getStatus() == OrderStatus.PREPARING && next == OrderStatus.READY);
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid order status transition");
        }
        transition(order, next);
        return toResponse(order);
    }

    @Transactional
    public OrderDtos.OrderResponse cancel(UUID id, UUID customerId) {
        FoodOrder order = findOrder(id);
        if (!order.getCustomerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel another customer's order");
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only paid orders can be cancelled");
        }
        for (OrderLine line : order.getLines()) {
            catalog.release(internalToken, new CatalogClient.StockRequest(line.getItemId(), line.getQuantity()));
        }
        transition(order, OrderStatus.CANCELLED);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderDtos.DeliveryEligibilityResponse deliveryEligibility(UUID id) {
        FoodOrder order = findOrder(id);
        return new OrderDtos.DeliveryEligibilityResponse(order.getId(), order.getCustomerId(),
                order.getStatus() == OrderStatus.READY || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY);
    }

    @Transactional
    public void updateInternalStatus(UUID id, OrderStatus next) {
        FoodOrder order = findOrder(id);
        if (next == OrderStatus.OUT_FOR_DELIVERY && order.getStatus() != OrderStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only ready orders may go out for delivery");
        }
        if (next == OrderStatus.DELIVERED && order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only dispatched orders may be delivered");
        }
        transition(order, next);
    }

    private boolean compensate(List<CatalogClient.StockRequest> reservations, RuntimeException failure) {
        List<CatalogClient.StockRequest> reverse = new ArrayList<>(reservations);
        Collections.reverse(reverse);
        boolean compensationFailed = false;
        for (CatalogClient.StockRequest reservation : reverse) {
            try {
                catalog.release(internalToken, reservation);
            } catch (RuntimeException compensationFailure) {
                compensationFailed = true;
                failure.addSuppressed(compensationFailure);
            }
        }
        return compensationFailed;
    }

    private FoodOrder findOrder(UUID id) {
        return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void transition(FoodOrder order, OrderStatus next) {
        order.setStatus(next);
        addStatus(order, next);
    }

    private void addStatus(FoodOrder order, OrderStatus status) {
        OrderStatusEvent event = new OrderStatusEvent();
        event.setOrder(order);
        event.setStatus(status);
        event.setChangedAt(Instant.now());
        order.getStatusHistory().add(event);
    }

    private OrderDtos.OrderResponse toResponse(FoodOrder order) {
        return new OrderDtos.OrderResponse(order.getId(), order.getCustomerId(), order.getTotal(), order.getStatus(),
                order.getCreatedAt(), order.getLines().stream()
                .map(line -> new OrderDtos.LineResponse(line.getItemId(), line.getItemName(), line.getQuantity(), line.getUnitPrice()))
                .toList(), order.getStatusHistory().stream()
                .map(event -> new OrderDtos.StatusEventResponse(event.getStatus(), event.getChangedAt()))
                .toList());
    }
}
