package com.foodflow.catalog.config;

import com.foodflow.catalog.domain.FoodItem;
import com.foodflow.catalog.domain.FoodItemRepository;
import com.foodflow.catalog.domain.Restaurant;
import com.foodflow.catalog.domain.RestaurantRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.UUID;

@Configuration
public class SampleData {
    public static final UUID RESTAURANT_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-4000-8000-000000000002");

    @Bean
    ApplicationRunner seedSampleMenu(RestaurantRepository restaurants, FoodItemRepository items) {
        return args -> {
            if (restaurants.count() == 0) {
                Restaurant restaurant = new Restaurant();
                restaurant.setId(RESTAURANT_ID);
                restaurant.setName("Sample Kitchen");
                restaurant.setAddress("12 Market Street");
                restaurant.setOwnerId(OWNER_ID);
                restaurants.save(restaurant);
                saveItem(items, RESTAURANT_ID, "Paneer wrap", "Grilled paneer, greens, and mint chutney.", "8.50", 12);
                saveItem(items, RESTAURANT_ID, "Garden bowl", "Seasonal vegetables with lemon dressing.", "7.25", 8);
                saveItem(items, RESTAURANT_ID, "Masala fries", "Crispy fries tossed with house spices.", "3.75", 15);
            }
        };
    }

    private void saveItem(FoodItemRepository items, UUID restaurantId, String name, String description,
                         String price, int quantity) {
        FoodItem item = new FoodItem();
        item.setRestaurantId(restaurantId);
        item.setName(name);
        item.setDescription(description);
        item.setPrice(new BigDecimal(price));
        item.setAvailable(true);
        item.setStockQuantity(quantity);
        items.save(item);
    }
}
