//package com.foodflow.catalog.service;
//
//import com.foodflow.catalog.dto.MenuItemRequest;
//import com.foodflow.catalog.entity.MenuItem;
//import com.foodflow.catalog.entity.Restaurant;
//import com.foodflow.catalog.repository.MenuItemRepository;
//import com.foodflow.catalog.repository.RestaurantRepository;
//import java.math.BigDecimal;
//import java.util.Optional;
//import java.util.UUID;
//import org.junit.jupiter.api.Test;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.web.server.ResponseStatusException;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//class CatalogServiceTest {
//  @Test
//  void ownerApprovalAttachesMatchingLegacyMenuItemsToTheRestaurant() {
//    MenuItemRepository menuItems = mock(MenuItemRepository.class);
//    RestaurantRepository restaurants = mock(RestaurantRepository.class);
//    UUID ownerId = UUID.randomUUID();
//    UUID restaurantId = UUID.randomUUID();
//    MenuItem legacyItem = new MenuItem();
//    legacyItem.setRestaurantName("Pavan Kitchen");
//    when(restaurants.findByOwnerId(ownerId)).thenReturn(Optional.empty());
//    when(restaurants.findByOwnerIdIsNullAndNameIgnoreCase("Pavan Kitchen"))
//        .thenReturn(Optional.empty());
//    when(restaurants.save(any(Restaurant.class))).thenAnswer(invocation -> {
//      Restaurant restaurant = invocation.getArgument(0);
//      restaurant.setId(restaurantId);
//      return restaurant;
//    });
//    when(menuItems.findByRestaurantIdIsNullAndRestaurantNameIgnoreCase("Pavan Kitchen"))
//        .thenReturn(java.util.List.of(legacyItem));
//    CatalogService service = new CatalogService(menuItems, restaurants);
//
//    Restaurant result = service.createRestaurant(" Pavan Kitchen ", ownerId);
//
//    assertEquals(restaurantId, result.getId());
//    assertEquals(restaurantId, legacyItem.getRestaurantId());
//    assertEquals("ACTIVE", result.getStatus());
//    org.junit.jupiter.api.Assertions.assertTrue(result.isAcceptingOrders());
//  }
//
//  @Test
//  void missingItemReturnsNotFound() {
//    MenuItemRepository menuItems = mock(MenuItemRepository.class);
//    when(menuItems.findById(any(UUID.class))).thenReturn(Optional.empty());
//    CatalogService service = new CatalogService(menuItems, mock(RestaurantRepository.class));
//
//    assertThrows(ResponseStatusException.class, () -> service.get(UUID.randomUUID()));
//  }
//
//  @Test
//  void ownerCannotEditAnotherRestaurantsMenu() {
//    MenuItemRepository menuItems = mock(MenuItemRepository.class);
//    RestaurantRepository restaurants = mock(RestaurantRepository.class);
//    MenuItem item = new MenuItem();
//    item.setId(UUID.randomUUID());
//    item.setRestaurantId(UUID.randomUUID());
//    when(menuItems.findById(any(UUID.class))).thenReturn(Optional.of(item));
//    Restaurant ownRestaurant = new Restaurant();
//    ownRestaurant.setId(UUID.randomUUID());
//    when(restaurants.findByOwnerId(any(UUID.class))).thenReturn(Optional.of(ownRestaurant));
//    CatalogService service = new CatalogService(menuItems, restaurants);
//    MenuItemRequest request = new MenuItemRequest("Spoofed", "Dish", "", BigDecimal.TEN, 2);
//
//    assertThrows(ResponseStatusException.class,
//        () -> service.update(UUID.randomUUID(), request, UUID.randomUUID()));
//  }
//
//  @Test
//  void ownerMenuItemUsesApprovedRestaurantIdentity() {
//    MenuItemRepository menuItems = mock(MenuItemRepository.class);
//    RestaurantRepository restaurants = mock(RestaurantRepository.class);
//    UUID ownerId = UUID.randomUUID();
//    Restaurant ownRestaurant = new Restaurant();
//    ownRestaurant.setId(UUID.randomUUID());
//    ownRestaurant.setName("Approved Kitchen");
//    when(restaurants.findByOwnerId(ownerId)).thenReturn(Optional.of(ownRestaurant));
//    when(menuItems.save(any(MenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
//    CatalogService service = new CatalogService(menuItems, restaurants);
//
//    MenuItem created = service.create(
//        new MenuItemRequest("Untrusted Name", "Dish", "", BigDecimal.TEN, 3), ownerId);
//
//    assertEquals(ownRestaurant.getId(), created.getRestaurantId());
//    assertEquals("Approved Kitchen", created.getRestaurantName());
//  }
//
//  @Test
//  void restaurantCreationIsIdempotentForAnAlreadyLinkedOwner() {
//    MenuItemRepository menuItems = mock(MenuItemRepository.class);
//    RestaurantRepository restaurants = mock(RestaurantRepository.class);
//    UUID ownerId = UUID.randomUUID();
//    Restaurant existing = new Restaurant();
//    existing.setId(UUID.randomUUID());
//    existing.setOwnerId(ownerId);
//    existing.setName("Munna Kitchen");
//    when(restaurants.findByOwnerId(ownerId)).thenReturn(Optional.of(existing));
//    CatalogService service = new CatalogService(menuItems, restaurants);
//
//    Restaurant result = service.createRestaurant("Munna Kitchen", ownerId);
//
//    assertEquals(existing.getId(), result.getId());
//    org.mockito.Mockito.verify(restaurants, org.mockito.Mockito.never()).save(any(Restaurant.class));
//  }
//
//  @Test
//  void ownerCanListAllOwnMenuItemsIncludingUnavailableItems() {
//    MenuItemRepository menuItems = mock(MenuItemRepository.class);
//    RestaurantRepository restaurants = mock(RestaurantRepository.class);
//    UUID ownerId = UUID.randomUUID();
//    Restaurant restaurant = new Restaurant();
//    restaurant.setId(UUID.randomUUID());
//    when(restaurants.findByOwnerId(ownerId)).thenReturn(Optional.of(restaurant));
//    MenuItem unavailable = new MenuItem();
//    unavailable.setAvailable(false);
//    when(menuItems.findByRestaurantId(restaurant.getId(), PageRequest.of(0, 10)))
//        .thenReturn(new PageImpl<>(java.util.List.of(unavailable)));
//    CatalogService service = new CatalogService(menuItems, restaurants);
//
//    var result = service.myRestaurantMenu(ownerId, PageRequest.of(0, 10));
//
//    assertEquals(1, result.getTotalElements());
//    org.junit.jupiter.api.Assertions.assertFalse(result.getContent().get(0).isAvailable());
//  }
//
//  @Test
//  void cannotReserveItemsWhenRestaurantIsNotAcceptingOrders() {
//    MenuItemRepository menuItems = mock(MenuItemRepository.class);
//    RestaurantRepository restaurants = mock(RestaurantRepository.class);
//    UUID restaurantId = UUID.randomUUID();
//    MenuItem item = new MenuItem();
//    item.setRestaurantId(restaurantId);
//    item.setAvailable(true);
//    item.setAvailableQuantity(2);
//    when(menuItems.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
//    Restaurant restaurant = new Restaurant();
//    restaurant.setId(restaurantId);
//    restaurant.setAcceptingOrders(false);
//    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
//    CatalogService service = new CatalogService(menuItems, restaurants);
//
//    assertThrows(ResponseStatusException.class, () -> service.reserve(item.getId(), 1));
//  }
//}
