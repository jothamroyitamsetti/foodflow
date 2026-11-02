package com.foodflow.auth.client;

import java.util.Map;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "catalog-service", url = "${foodflow.catalog.url:}")
public interface CatalogClient {
	@PostMapping("/api/catalog/internal/restaurants")
	void createRestaurant(@RequestHeader("X-Auth-User-Id") UUID adminId,
			@RequestHeader("X-Auth-User-Role") String adminRole, @RequestBody Map<String, Object> restaurant);
}
