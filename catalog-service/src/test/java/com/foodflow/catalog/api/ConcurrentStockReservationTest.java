package com.foodflow.catalog.api;

import com.foodflow.catalog.domain.FoodItem;
import com.foodflow.catalog.domain.FoodItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConcurrentStockReservationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("catalog_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired CatalogService catalog;
    @Autowired FoodItemRepository items;

    @Test
    void onlyOneIndependentTransactionCanReserveTheLastItem() throws Exception {
        FoodItem item = new FoodItem();
        item.setRestaurantId(UUID.randomUUID());
        item.setName("Last portion");
        item.setPrice(new BigDecimal("9.99"));
        item.setAvailable(true);
        item.setStockQuantity(1);
        UUID id = items.saveAndFlush(item).getId();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> attempt = () -> {
                start.await();
                try {
                    catalog.reserve(id, 1);
                    succeeded.incrementAndGet();
                } catch (org.springframework.web.server.ResponseStatusException e) {
                    conflicted.incrementAndGet();
                }
                return null;
            };
            Future<Void> first = pool.submit(attempt);
            Future<Void> second = pool.submit(attempt);
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, succeeded.get());
        assertEquals(1, conflicted.get());
        assertEquals(0, items.findById(id).orElseThrow().getStockQuantity());
    }
}
