package com.foodflow.catalog.service;

import com.foodflow.catalog.dto.MenuItemRequest;
import com.foodflow.catalog.entity.MenuItem;
import com.foodflow.catalog.entity.Restaurant;
import com.foodflow.catalog.repository.MenuItemRepository;
import com.foodflow.catalog.repository.RestaurantRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CatalogService {
  private final MenuItemRepository menuItems;
  private final RestaurantRepository restaurants;

  public Page<MenuItem> list(Pageable pageable) {
    return menuItems.findAll(pageable);
  }

  public Page<MenuItem> available(Pageable pageable) {
    return menuItems.findByRestaurantIdIsNotNullAndAvailableTrue(pageable);
  }

  public MenuItem get(UUID id) {
    return menuItems.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
  }

  public List<Restaurant> restaurants() {
    return restaurants.findByStatusAndAcceptingOrdersOrderByNameAsc("ACTIVE", true);
  }

  public List<Restaurant> allRestaurants() {
    return restaurants.findAllByOrderByNameAsc();
  }

  public Restaurant myRestaurant(UUID ownerId) {
    return restaurants.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "No approved restaurant is linked to this owner"));
  }

  public Page<MenuItem> myRestaurantMenu(UUID ownerId, Pageable pageable) {
    Restaurant restaurant = myRestaurant(ownerId);
    return menuItems.findByRestaurantId(restaurant.getId(), pageable);
  }

  @Transactional
  public Restaurant createRestaurant(String name, UUID ownerId) {
    Restaurant linkedRestaurant = restaurants.findByOwnerId(ownerId).orElse(null);
    if (linkedRestaurant != null) {
      return linkedRestaurant;
    }
    String restaurantName = name.trim();
    Restaurant restaurant = restaurants.findByOwnerIdIsNullAndNameIgnoreCase(restaurantName)
        .orElseGet(Restaurant::new);
    restaurant.setName(restaurantName);
    restaurant.setOwnerId(ownerId);
    restaurant.setStatus("ACTIVE");
    restaurant.setAcceptingOrders(true);
    Restaurant savedRestaurant = restaurants.save(restaurant);
    for (MenuItem legacyItem : menuItems.findByRestaurantIdIsNullAndRestaurantNameIgnoreCase(restaurantName)) {
      legacyItem.setRestaurantId(savedRestaurant.getId());
      legacyItem.setRestaurantName(savedRestaurant.getName());
    }
    return savedRestaurant;
  }

  public Page<MenuItem> restaurantMenu(UUID restaurantId, Pageable pageable) {
    Restaurant restaurant = restaurants.findById(restaurantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
    if (!isOrderable(restaurant)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found");
    }
    return menuItems.findByRestaurantIdAndAvailableTrue(restaurantId, pageable);
  }

  public MenuItem create(MenuItemRequest request, UUID userId) {
    return saveFields(new MenuItem(), request, myRestaurant(userId));
  }

  public Restaurant updateAcceptingOrders(UUID ownerId, boolean acceptingOrders) {
    Restaurant restaurant = myRestaurant(ownerId);
    restaurant.setAcceptingOrders(acceptingOrders);
    return restaurants.save(restaurant);
  }

  public Restaurant updateRestaurantStatus(UUID restaurantId, String status) {
    if (!List.of("ACTIVE", "INACTIVE", "SUSPENDED").contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid restaurant status");
    }
    Restaurant restaurant = restaurants.findById(restaurantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
    restaurant.setStatus(status);
    if (!"ACTIVE".equals(status)) {
      restaurant.setAcceptingOrders(false);
    }
    return restaurants.save(restaurant);
  }

  public MenuItem update(UUID id, MenuItemRequest request, UUID userId) {
    MenuItem item = get(id);
    assertOwner(item, userId);
    return saveFields(item, request, findRestaurant(item.getRestaurantId()));
  }

  public void delete(UUID id, UUID userId) {
    MenuItem item = get(id);
    assertOwner(item, userId);
    menuItems.delete(item);
  }

  @Transactional
  public MenuItem reserve(UUID id, int quantity) {
    if (quantity < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
    }
    MenuItem item = menuItems.findByIdForUpdate(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
    if (item.getRestaurantId() == null
        || !isOrderable(restaurants.findById(item.getRestaurantId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Menu item restaurant not found")))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Restaurant is not accepting orders");
    }
    if (!item.isAvailable() || item.getAvailableQuantity() < quantity) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock");
    }
    item.setAvailableQuantity(item.getAvailableQuantity() - quantity);
    item.setAvailable(item.getAvailableQuantity() > 0);
    return item;
  }

  @Transactional
  public void release(UUID id, int quantity) {
    MenuItem item = menuItems.findByIdForUpdate(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found"));
    item.setAvailableQuantity(item.getAvailableQuantity() + Math.max(0, quantity));
    item.setAvailable(true);
  }

  private Restaurant findRestaurant(UUID restaurantId) {
    if (restaurantId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a restaurant");
    }
    return restaurants.findById(restaurantId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a restaurant"));
  }

  private boolean isOrderable(Restaurant restaurant) {
    return "ACTIVE".equals(restaurant.getStatus()) && restaurant.isAcceptingOrders();
  }

  private void assertOwner(MenuItem item, UUID userId) {
    Restaurant restaurant = myRestaurant(userId);
    if (!restaurant.getId().equals(item.getRestaurantId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can manage only your restaurant menu");
    }
  }

  private MenuItem saveFields(MenuItem item, MenuItemRequest request, Restaurant restaurant) {
    item.setRestaurantId(restaurant.getId());
    item.setRestaurantName(restaurant.getName());
    item.setName(request.name());
    item.setDescription(request.description());
    item.setPrice(request.price());
    item.setAvailableQuantity(request.availableQuantity());
    item.setAvailable(request.availableQuantity() > 0);
    return menuItems.save(item);
  }
}
