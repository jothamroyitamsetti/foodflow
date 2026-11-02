package com.foodflow.orders.controller;

import com.foodflow.orders.dto.CreateOrderRequest;
import com.foodflow.orders.entity.Order;
import com.foodflow.orders.dto.CreatePaymentResponse;
import com.foodflow.orders.dto.VerifyPaymentRequest;
import com.foodflow.orders.dto.RefundResponse;
import com.foodflow.orders.service.RazorpayPaymentService;
import com.foodflow.orders.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
	private final OrderService service;
	private final RazorpayPaymentService payments;

	private UUID uid(Authentication a) {
		return UUID.fromString(a.getName());
	}

	@PostMapping
	@PreAuthorize("hasRole('CUSTOMER')")
	@ResponseStatus(HttpStatus.CREATED)
	public Order create(Authentication a, @RequestHeader("Idempotency-Key") String key,
			@Valid @RequestBody CreateOrderRequest r) {
		return service.create(uid(a), key, r);
	}

	@PostMapping("/{id}/payment")
	@PreAuthorize("hasRole('CUSTOMER')")
	public CreatePaymentResponse createPayment(Authentication authentication, @PathVariable UUID id) {
		return payments.create(id, uid(authentication));
	}

	@PostMapping("/{id}/payment/verify")
	@PreAuthorize("hasRole('CUSTOMER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void verifyPayment(
			Authentication authentication,
			@PathVariable UUID id,
			@Valid @RequestBody VerifyPaymentRequest request) {
		payments.verify(id, uid(authentication), request);
	}

	@PostMapping("/{id}/cancel")
	@PreAuthorize("hasRole('CUSTOMER')")
	public Order cancel(Authentication authentication, @PathVariable UUID id) {
		return service.cancel(id, uid(authentication));
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasRole('RESTAURANT_OWNER')")
	public Order reject(Authentication authentication, @PathVariable UUID id) {
		Order order = service.restaurantOrderForRejection(id);
		if ("RAZORPAY".equals(order.getPaymentMethod())
				&& ("PAID".equals(order.getPaymentStatus()) || "REFUND_PENDING".equals(order.getPaymentStatus()))) {
			payments.refundForRestaurantRejection(id, uid(authentication));
		}
		return service.rejectRestaurantOrder(id, uid(authentication));
	}

	@PostMapping("/{id}/refund")
	@PreAuthorize("hasRole('ADMIN')")
	public RefundResponse refund(Authentication authentication, @PathVariable UUID id) {
		return payments.refund(id, uid(authentication));
	}

	@GetMapping("/{id}/refund")
	@PreAuthorize("hasRole('ADMIN')")
	public RefundResponse refundStatus(@PathVariable UUID id) {
		return payments.refreshRefund(id);
	}

	@GetMapping("/mine")
	@PreAuthorize("hasRole('CUSTOMER')")
	public Page<Order> mine(Authentication a, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return service.mine(uid(a), PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)),
				Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@GetMapping("/management")
	@PreAuthorize("hasAnyRole('ADMIN','RESTAURANT_OWNER')")
	public Page<Order> management(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size, Authentication authentication) {
		boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
		return service.management(PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)),
				Sort.by(Sort.Direction.DESC, "createdAt")), admin);
	}

	@GetMapping("/{id}")
	public Order get(Authentication a, @PathVariable UUID id) {
		boolean admin = a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN"));
		return service.get(id, uid(a), admin);
	}

	@PatchMapping("/{id}/status")
	@PreAuthorize("hasAnyRole('ADMIN','RESTAURANT_OWNER')")
	public Order status(@PathVariable UUID id, @RequestParam String value, Authentication authentication) {
		boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
		return service.updateManagedStatus(id, value, admin, uid(authentication));
	}
}
