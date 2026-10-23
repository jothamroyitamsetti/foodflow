package com.foodflow.catalog.api;

import com.foodflow.catalog.domain.FoodItem;
import com.foodflow.catalog.domain.FoodItemRepository;
import com.foodflow.catalog.domain.Restaurant;
import com.foodflow.catalog.domain.RestaurantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CatalogService {
    private final RestaurantRepository restaurants;
    private final FoodItemRepository items;

    public CatalogService(RestaurantRepository restaurants, FoodItemRepository items) {
        this.restaurants = restaurants;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public Page<CatalogDtos.RestaurantResponse> restaurants(Pageable pageable) {
        return restaurants.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CatalogDtos.RestaurantResponse restaurant(UUID id) {
        return restaurants.findById(id).map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
    }

    @Transactional
    public CatalogDtos.RestaurantResponse createRestaurant(CatalogDtos.RestaurantRequest request, UUID ownerId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.name().trim());
        restaurant.setAddress(request.address().trim());
        restaurant.setOwnerId(ownerId);
        return toResponse(restaurants.save(restaurant));
    }

    @Transactional(readOnly = true)
    public Page<CatalogDtos.FoodItemResponse> items(UUID restaurantId, String search, Pageable pageable) {
        Page<FoodItem> result = (search == null || search.isBlank())
                ? items.findByRestaurantIdAndAvailableTrue(restaurantId, pageable)
                : items.findByRestaurantIdAndNameContainingIgnoreCaseAndAvailableTrue(restaurantId, search.trim(), pageable);
        return result.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CatalogDtos.FoodItemResponse item(UUID id) {
        return items.findById(id).map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food item not found"));
    }

    @Transactional
    public CatalogDtos.FoodItemResponse createItem(CatalogDtos.FoodItemRequest request, UUID actorId, boolean admin) {
        Restaurant restaurant = restaurants.findById(request.restaurantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        ensureOwnerOrAdmin(restaurant, actorId, admin);
        FoodItem item = new FoodItem();
        item.setRestaurantId(request.restaurantId());
        apply(item, request);
        return toResponse(items.save(item));
    }

    @Transactional
    public CatalogDtos.FoodItemResponse updateItem(UUID id, CatalogDtos.FoodItemRequest request, UUID actorId, boolean admin) {
        FoodItem item = items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food item not found"));
        ensureOwnerOrAdmin(item.getRestaurantId(), actorId, admin);
        if (!item.getRestaurantId().equals(request.restaurantId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food item restaurant cannot be changed");
        }
        apply(item, request);
        return toResponse(item);
    }

    @Transactional
    public void deleteItem(UUID id, UUID actorId, boolean admin) {
        FoodItem item = items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food item not found"));
        ensureOwnerOrAdmin(item.getRestaurantId(), actorId, admin);
        items.delete(item);
    }

    @Transactional
    public CatalogDtos.StockResponse reserve(UUID itemId, int quantity) {
        FoodItem item = items.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food item not found"));
        if (!item.isAvailable() || item.getStockQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient inventory");
        }
        item.setStockQuantity(item.getStockQuantity() - quantity);
        return new CatalogDtos.StockResponse(item.getId(), item.getStockQuantity());
    }

    @Transactional
    public CatalogDtos.StockResponse release(UUID itemId, int quantity) {
        FoodItem item = items.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food item not found"));
        item.setStockQuantity(Math.addExact(item.getStockQuantity(), quantity));
        return new CatalogDtos.StockResponse(item.getId(), item.getStockQuantity());
    }

    private void apply(FoodItem item, CatalogDtos.FoodItemRequest request) {
        item.setName(request.name().trim());
        item.setDescription(request.description());
        item.setPrice(request.price());
        item.setStockQuantity(request.stockQuantity());
        item.setAvailable(request.stockQuantity() > 0);
    }

    private void ensureOwnerOrAdmin(UUID restaurantId, UUID actorId, boolean admin) {
        if (admin) {
            return;
        }
        Restaurant restaurant = restaurants.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found"));
        ensureOwnerOrAdmin(restaurant, actorId, false);
    }

    private void ensureOwnerOrAdmin(Restaurant restaurant, UUID actorId, boolean admin) {
        if (!admin && !restaurant.getOwnerId().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this restaurant");
        }
    }

    private CatalogDtos.RestaurantResponse toResponse(Restaurant restaurant) {
        return new CatalogDtos.RestaurantResponse(restaurant.getId(), restaurant.getName(),
                restaurant.getAddress(), restaurant.getOwnerId(), restaurant.isActive());
    }

    private CatalogDtos.FoodItemResponse toResponse(FoodItem item) {
        return new CatalogDtos.FoodItemResponse(item.getId(), item.getRestaurantId(), item.getName(),
                item.getDescription(), item.getPrice(), item.isAvailable(), item.getStockQuantity());
    }
}
