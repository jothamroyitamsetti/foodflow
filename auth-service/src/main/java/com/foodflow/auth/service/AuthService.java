package com.foodflow.auth.service;

import com.foodflow.auth.dto.*;
import com.foodflow.auth.client.CatalogClient;
import com.foodflow.auth.entity.RoleApplication;
import com.foodflow.auth.entity.User;
import com.foodflow.auth.repository.RoleApplicationRepository;
import com.foodflow.auth.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
	private static final long DEFAULT_TOKEN_EXPIRATION_SECONDS = 12 * 60 * 60;
	private final UserRepository repo;
	private final PasswordEncoder encoder;
	private final RoleApplicationRepository applications;
	private final CatalogClient catalog;
	private final javax.crypto.SecretKey key;

	@Value("${foodflow.jwt.access-token-expiration-seconds:43200}")
	private long tokenExpirationSeconds = DEFAULT_TOKEN_EXPIRATION_SECONDS;

	public AuthService(UserRepository r, PasswordEncoder e, RoleApplicationRepository applications,
			CatalogClient catalog, @Value("${foodflow.jwt.secret}") String secret) {
		repo = r;
		encoder = e;
		this.applications = applications;
		this.catalog = catalog;
		key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	@Transactional
	public RegistrationResponse register(RegisterRequest req) {
		if (repo.existsByEmail(req.email().toLowerCase()))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
		User u = new User();
		u.setFullName(req.fullName().trim());
		u.setEmail(req.email().toLowerCase().trim());
		u.setPasswordHash(encoder.encode(req.password()));
		u.setRole("CUSTOMER");
		String requestedRole = req.applicationRole() == null ? "CUSTOMER" : req.applicationRole();
		if (!List.of("CUSTOMER", "RESTAURANT_OWNER", "DELIVERY_PARTNER").contains(requestedRole)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported account application role");
		}
		User saved = repo.save(u);
		if (!"CUSTOMER".equals(requestedRole)) {
			if ("RESTAURANT_OWNER".equals(requestedRole)
					&& (req.restaurantName() == null || req.restaurantName().isBlank())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restaurant name is required for owner applications");
			}
			if (applications.existsByUserIdAndRequestedRoleAndStatus(saved.getId(), requestedRole, "PENDING")) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "A matching application is already pending");
			}
			RoleApplication application = new RoleApplication();
			application.setUserId(saved.getId());
			application.setFullName(saved.getFullName());
			application.setEmail(saved.getEmail());
			application.setRequestedRole(requestedRole);
			application.setRestaurantName(req.restaurantName() == null ? null : req.restaurantName().trim());
			applications.save(application);
		}
		return new RegistrationResponse(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole());
	}

	public List<RoleApplication> applications() {
		return applications.findAllByOrderByCreatedAtDesc();
	}

	public List<RoleApplication> myApplications(UUID userId) {
		return applications.findByUserIdOrderByCreatedAtDesc(userId);
	}

	@Transactional
	public RoleApplication applyForRole(UUID userId, String requestedRole, String restaurantName) {
		if (!List.of("RESTAURANT_OWNER", "DELIVERY_PARTNER").contains(requestedRole)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported account application role");
		}
		User user = repo.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
		if (requestedRole.equals(user.getRole())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This account already has the requested role");
		}
		if (applications.existsByUserIdAndRequestedRoleAndStatus(userId, requestedRole, "PENDING")) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A matching application is already pending");
		}
		String normalizedRestaurantName = restaurantName == null ? null : restaurantName.trim();
		if ("RESTAURANT_OWNER".equals(requestedRole)
				&& (normalizedRestaurantName == null || normalizedRestaurantName.isBlank())) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "Restaurant name is required for owner applications");
		}

		RoleApplication application = new RoleApplication();
		application.setUserId(user.getId());
		application.setFullName(user.getFullName());
		application.setEmail(user.getEmail());
		application.setRequestedRole(requestedRole);
		application.setRestaurantName("RESTAURANT_OWNER".equals(requestedRole) ? normalizedRestaurantName : null);
		return applications.save(application);
	}

	@Transactional
	public void ensureOwnerRestaurant(UUID userId) {
		User user = repo.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
		if (!"RESTAURANT_OWNER".equals(user.getRole())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Restaurant-owner approval is required");
		}
		RoleApplication application = applications.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.filter(candidate -> "RESTAURANT_OWNER".equals(candidate.getRequestedRole())
						&& "APPROVED".equals(candidate.getStatus()))
				.findFirst()
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.CONFLICT, "No approved restaurant-owner application was found"));
		if (application.getRestaurantName() == null || application.getRestaurantName().isBlank()) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "The approved application has no restaurant name; contact an administrator");
		}
		catalog.createRestaurant(userId, "ADMIN", Map.of(
				"name", application.getRestaurantName(),
				"ownerId", userId));
	}

	public Page<AdminUserResponse> users(Pageable pageable) {
		return repo.findAll(pageable).map(user -> new AdminUserResponse(
				user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getCreatedAt()));
	}

	@Transactional
	public RoleApplication approve(UUID applicationId, UUID adminId) {
		RoleApplication application = applications.findById(applicationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
		if (!"PENDING".equals(application.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Application has already been reviewed");
		}
		if (!List.of("RESTAURANT_OWNER", "DELIVERY_PARTNER").contains(application.getRequestedRole())) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "Only restaurant-owner and delivery-partner applications can be approved");
		}
		User user = repo.findById(application.getUserId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Applicant account not found"));
		if ("RESTAURANT_OWNER".equals(application.getRequestedRole())) {
			catalog.createRestaurant(adminId, "ADMIN", Map.of(
					"name", application.getRestaurantName(),
					"ownerId", application.getUserId()));
		}
		user.setRole(application.getRequestedRole());
		application.setStatus("APPROVED");
		repo.save(user);
		return applications.save(application);
	}

	@Transactional
	public RoleApplication reject(UUID applicationId, String reason) {
		if (reason == null || reason.isBlank() || reason.length() > 500) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "A rejection reason of up to 500 characters is required");
		}
		RoleApplication application = applications.findById(applicationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
		if (!"PENDING".equals(application.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Application has already been reviewed");
		}
		application.setStatus("REJECTED");
		application.setRejectionReason(reason.trim());
		return applications.save(application);
	}

	public AuthResponse login(LoginRequest req) {
		User u = repo.findByEmail(req.email().toLowerCase().trim())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

		if (!encoder.matches(req.password(), u.getPasswordHash()))
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
		return token(u);
	}

	private AuthResponse token(User u) {
		Instant now = Instant.now();
		String t = Jwts.builder().subject(u.getId().toString()).claim("role", u.getRole()).claim("email", u.getEmail())
				.issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(tokenExpirationSeconds)))
				.signWith(key, Jwts.SIG.HS256).compact();
		return new AuthResponse(t, "Bearer", u.getId(), u.getFullName(), u.getEmail(), u.getRole());
	}
}
