package com.foodflow.auth.controller;

import com.foodflow.auth.dto.*;
import com.foodflow.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.UUID;
import com.foodflow.auth.entity.RoleApplication;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	private final AuthService service;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegistrationResponse register(@Valid @RequestBody RegisterRequest r) {
		return service.register(r);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest r) {
		return service.login(r);
	}

	@GetMapping("/applications")
	@PreAuthorize("hasRole('ADMIN')")
	public List<RoleApplication> applications() {
		return service.applications();
	}

	@GetMapping("/admin/users")
	@PreAuthorize("hasRole('ADMIN')")
	public org.springframework.data.domain.Page<AdminUserResponse> users(
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return service.users(PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)),
				Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@GetMapping("/applications/mine")
	@PreAuthorize("hasAnyRole('CUSTOMER','RESTAURANT_OWNER','DELIVERY_PARTNER')")
	public List<RoleApplication> myApplications(Authentication authentication) {
		return service.myApplications(UUID.fromString(authentication.getName()));
	}

	@PostMapping("/applications/mine")
	@PreAuthorize("hasRole('CUSTOMER')")
	@ResponseStatus(HttpStatus.CREATED)
	public RoleApplication applyForRole(
			@Valid @RequestBody RoleApplicationRequest request, Authentication authentication) {
		return service.applyForRole(
				UUID.fromString(authentication.getName()), request.requestedRole(), request.restaurantName());
	}

	@PostMapping("/applications/mine/restaurant")
	@PreAuthorize("hasRole('RESTAURANT_OWNER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void ensureOwnerRestaurant(Authentication authentication) {
		service.ensureOwnerRestaurant(UUID.fromString(authentication.getName()));
	}

	@PostMapping("/applications/{id}/approve")
	@PreAuthorize("hasRole('ADMIN')")
	public RoleApplication approve(@PathVariable UUID id, Authentication authentication) {
		return service.approve(id, UUID.fromString(authentication.getName()));
	}

	@PostMapping("/applications/{id}/reject")
	@PreAuthorize("hasRole('ADMIN')")
	public RoleApplication reject(@PathVariable UUID id, @Valid @RequestBody RejectionRequest request) {
		return service.reject(id, request.reason());
	}
}
