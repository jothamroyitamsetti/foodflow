//package com.foodflow.catalog.service;
//
//import com.foodflow.catalog.entity.MenuItem;
//import com.foodflow.catalog.repository.MenuItemRepository;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.HttpStatus;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.springframework.web.server.ResponseStatusException;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.math.BigDecimal;
//import java.util.concurrent.*;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//@Testcontainers(disabledWithoutDocker = true)
//@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
//class CatalogConcurrencyIntegrationTest {
//    @Container
//    @SuppressWarnings("resource")
//    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
//            .withDatabaseName("foodflow_catalog_test")
//            .withUsername("test")
//            .withPassword("test");
//
//    @DynamicPropertySource
//    static void databaseProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
//        registry.add("spring.datasource.username", POSTGRES::getUsername);
//        registry.add("spring.datasource.password", POSTGRES::getPassword);
//        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
//    }
//
//    @Autowired CatalogService service;
//    @Autowired MenuItemRepository repository;
//
//    @Test
//    void onlyOneConcurrentReservationCanBuyTheLastItem() throws Exception {
//        MenuItem item = new MenuItem();
//        item.setRestaurantName("Test Kitchen");
//        item.setName("Last Burger");
//        item.setPrice(new BigDecimal("120.00"));
//        item.setAvailableQuantity(1);
//        item.setAvailable(true);
//        item = repository.saveAndFlush(item);
//        var itemId = item.getId();
//
//        CountDownLatch ready = new CountDownLatch(2);
//        CountDownLatch start = new CountDownLatch(1);
//        ExecutorService pool = Executors.newFixedThreadPool(2);
//        try {
//            Callable<Boolean> attempt = () -> {
//                ready.countDown();
//                start.await(5, TimeUnit.SECONDS);
//                try {
//                    service.reserve(itemId, 1);
//                    return true;
//                } catch (ResponseStatusException ex) {
//                    if (ex.getStatusCode() != HttpStatus.CONFLICT) throw ex;
//                    return false;
//                }
//            };
//            Future<Boolean> first = pool.submit(attempt);
//            Future<Boolean> second = pool.submit(attempt);
//            ready.await(5, TimeUnit.SECONDS);
//            start.countDown();
//            int successfulReservations = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
//                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
//            assertEquals(1, successfulReservations);
//            assertEquals(0, repository.findById(itemId).orElseThrow().getAvailableQuantity());
//        } finally {
//            pool.shutdownNow();
//        }
//    }
//}
