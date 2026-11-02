package com.foodflow.catalog.controller;

import com.foodflow.catalog.dto.MenuItemRequest;
import com.foodflow.catalog.dto.StockRequest;
import com.foodflow.catalog.entity.MenuItem;
import com.foodflow.catalog.entity.Restaurant;
import com.foodflow.catalog.service.CatalogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {
	private final CatalogService service;

	@GetMapping("/restaurants")
	public List<Restaurant> restaurants() {
		return service.restaurants();
	}

	@GetMapping("/restaurants/admin")
	@PreAuthorize("hasRole('ADMIN')")
	public List<Restaurant> allRestaurants() {
		return service.allRestaurants();
	}

	@GetMapping("/restaurants/mine")
	@PreAuthorize("hasRole('RESTAURANT_OWNER')")
	public Restaurant myRestaurant(Authentication authentication) {
		return service.myRestaurant(userId(authentication));
	}

	@GetMapping("/restaurants/mine/menu")
	@PreAuthorize("hasRole('RESTAURANT_OWNER')")
	public Page<MenuItem> myRestaurantMenu(Authentication authentication, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.myRestaurantMenu(userId(authentication), pageable(page, size));
	}

	@PutMapping("/restaurants/mine/availability")
	@PreAuthorize("hasRole('RESTAURANT_OWNER')")
	public Restaurant acceptingOrders(Authentication authentication, @RequestParam boolean acceptingOrders) {
		return service.updateAcceptingOrders(userId(authentication), acceptingOrders);
	}

	@PutMapping("/restaurants/{id}/status")
	@PreAuthorize("hasRole('ADMIN')")
	public Restaurant restaurantStatus(@PathVariable UUID id, @RequestParam String status) {
		return service.updateRestaurantStatus(id, status);
	}

	@GetMapping("/restaurants/{id}/menu")
	public Page<MenuItem> restaurantMenu(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.restaurantMenu(id, pageable(page, size));
	}

	@PostMapping("/internal/restaurants")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public Restaurant createRestaurant(@RequestBody Map<String, Object> request) {
		Object rawName = request.get("name");
		Object rawOwner = request.get("ownerId");
		if (!(rawName instanceof String name) || name.isBlank() || rawOwner == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restaurant name and owner are required");
		}
		try {
			return service.createRestaurant(name, UUID.fromString(rawOwner.toString()));
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid owner id", exception);
		}
	}

	@GetMapping("/menu")
	public Page<MenuItem> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return service.available(pageable(page, size));
	}

	@GetMapping("/menu/{id}")
	public MenuItem get(@PathVariable UUID id) {
		return service.get(id);
	}

	@PostMapping("/menu")
	@PreAuthorize("hasRole('RESTAURANT_OWNER')")
	@ResponseStatus(HttpStatus.CREATED)
	public MenuItem create(@Valid @RequestBody MenuItemRequest request, Authentication authentication) {
		return service.create(request, userId(authentication));
	}

	@PutMapping("/menu/{id}")
	@PreAuthorize("hasRole('RESTAURANT_OWNER')")
	public MenuItem update(@PathVariable UUID id, @Valid @RequestBody MenuItemRequest request,
			Authentication authentication) {
		return service.update(id, request, userId(authentication));
	}

	@DeleteMapping("/menu/{id}")
	@PreAuthorize("hasRole('RESTAURANT_OWNER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id, Authentication authentication) {
		service.delete(id, userId(authentication));
	}

	@PostMapping("/internal/inventory/{id}/reserve")
	public MenuItem reserve(@PathVariable UUID id, @Valid @RequestBody StockRequest request) {
		return service.reserve(id, request.quantity());
	}

	@PostMapping("/internal/inventory/{id}/release")
	public void release(@PathVariable UUID id, @Valid @RequestBody StockRequest request) {
		service.release(id, request.quantity());
	}

	private Pageable pageable(int page, int size) {
		return PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)), Sort.by("name").ascending());
	}

	private UUID userId(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

}
