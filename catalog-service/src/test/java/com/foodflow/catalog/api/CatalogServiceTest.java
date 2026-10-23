package com.foodflow.catalog.api;

import com.foodflow.catalog.domain.FoodItem;
import com.foodflow.catalog.domain.FoodItemRepository;
import com.foodflow.catalog.domain.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final FoodItemRepository items = mock(FoodItemRepository.class);
    private final CatalogService service = new CatalogService(restaurants, items);

    @Test
    void reserveRejectsInsufficientInventoryWithoutChangingStock() {
        UUID id = UUID.randomUUID();
        FoodItem item = new FoodItem();
        item.setId(id);
        item.setStockQuantity(1);
        item.setAvailable(true);
        when(items.findByIdForUpdate(id)).thenReturn(Optional.of(item));

        assertThrows(ResponseStatusException.class, () -> service.reserve(id, 2));
        assertEquals(1, item.getStockQuantity());
    }
}
