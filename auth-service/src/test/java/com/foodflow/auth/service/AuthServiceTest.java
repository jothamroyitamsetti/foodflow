//package com.foodflow.auth.service;
//
//import com.foodflow.auth.dto.LoginRequest;
//import com.foodflow.auth.dto.RegisterRequest;
//import com.foodflow.auth.dto.RegistrationResponse;
//import com.foodflow.auth.client.CatalogClient;
//import com.foodflow.auth.entity.RoleApplication;
//import com.foodflow.auth.entity.User;
//import com.foodflow.auth.repository.RoleApplicationRepository;
//import com.foodflow.auth.repository.UserRepository;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.security.Keys;
//import java.nio.charset.StandardCharsets;
//import java.time.Instant;
//import java.util.Optional;
//import java.util.UUID;
//import java.util.List;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.PageRequest;
//import org.junit.jupiter.api.Test;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.web.server.ResponseStatusException;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.never;
//
//class AuthServiceTest {
//  private static final String SECRET = "FoodFlowLocalJwtSecretKeyMustBeAtLeastThirtyTwoCharsLong!";
//
//  @Test
//  void unknownUserCannotLogin() {
//    UserRepository repo = mock(UserRepository.class);
//    PasswordEncoder encoder = mock(PasswordEncoder.class);
//    when(repo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
//    AuthService service = service(repo, encoder, mock(RoleApplicationRepository.class));
//
//    assertThrows(ResponseStatusException.class,
//        () -> service.login(new LoginRequest("nobody@example.com", "password123")));
//  }
//
//  @Test
//  void registrationReturnsAccountDetailsWithoutIssuingToken() {
//    UserRepository repo = mock(UserRepository.class);
//    PasswordEncoder encoder = mock(PasswordEncoder.class);
//    when(repo.existsByEmail("pavan@example.com")).thenReturn(false);
//    when(repo.save(any(User.class))).thenAnswer(invocation -> {
//      User user = invocation.getArgument(0);
//      user.setId(UUID.randomUUID());
//      return user;
//    });
//
//    var response = service(repo, encoder, mock(RoleApplicationRepository.class))
//        .register(new RegisterRequest("Pavan", "pavan@example.com", "password123"));
//
//    assertEquals(RegistrationResponse.class, response.getClass());
//    assertEquals("CUSTOMER", response.role());
//  }
//
//  @Test
//  void ownerRegistrationCreatesPendingApplicationWithoutGrantingOwnerRole() {
//    UserRepository users = mock(UserRepository.class);
//    RoleApplicationRepository applications = mock(RoleApplicationRepository.class);
//    when(users.existsByEmail("owner@example.com")).thenReturn(false);
//    when(users.save(any(User.class))).thenAnswer(invocation -> {
//      User user = invocation.getArgument(0);
//      user.setId(UUID.randomUUID());
//      return user;
//    });
//
//    RegistrationResponse response = service(users, mock(PasswordEncoder.class), applications)
//        .register(new RegisterRequest(
//            "Owner", "owner@example.com", "password123", "RESTAURANT_OWNER", "Pavan Kitchen"));
//
//    assertEquals("CUSTOMER", response.role());
//    verify(applications).save(org.mockito.ArgumentMatchers.argThat(application ->
//        "PENDING".equals(application.getStatus())
//            && "RESTAURANT_OWNER".equals(application.getRequestedRole())
//            && "Pavan Kitchen".equals(application.getRestaurantName())));
//  }
//
//  @Test
//  void registrationCannotRequestAdministratorRole() {
//    UserRepository users = mock(UserRepository.class);
//    AuthService service = service(users, mock(PasswordEncoder.class), mock(RoleApplicationRepository.class));
//
//    ResponseStatusException error = assertThrows(ResponseStatusException.class,
//        () -> service.register(new RegisterRequest(
//            "Admin", "admin@example.com", "password123", "ADMIN", null)));
//
//    assertEquals(400, error.getStatusCode().value());
//    verify(users, never()).save(any(User.class));
//  }
//
//  @Test
//  void loginIssuesHs256Token() {
//    UserRepository repo = mock(UserRepository.class);
//    PasswordEncoder encoder = mock(PasswordEncoder.class);
//    User user = new User();
//    user.setId(UUID.randomUUID());
//    user.setEmail("pavan@example.com");
//    user.setFullName("Pavan");
//    user.setRole("CUSTOMER");
//    user.setPasswordHash("hashed");
//    when(repo.findByEmail("pavan@example.com")).thenReturn(Optional.of(user));
//    when(encoder.matches("password123", "hashed")).thenReturn(true);
//
//    var response = service(repo, encoder, mock(RoleApplicationRepository.class))
//        .login(new LoginRequest("pavan@example.com", "password123"));
//    var parsed = Jwts.parser()
//        .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
//        .build()
//        .parseSignedClaims(response.token());
//
//    assertEquals("HS256", parsed.getHeader().getAlgorithm());
//    long remainingSeconds = parsed.getPayload().getExpiration().toInstant().getEpochSecond()
//        - Instant.now().getEpochSecond();
//    assertTrue(remainingSeconds > 11 * 60 * 60 && remainingSeconds <= 12 * 60 * 60);
//  }
//
//  @Test
//  void approvalGrantsRoleAndCreatesOwnerRestaurant() {
//    UserRepository users = mock(UserRepository.class);
//    RoleApplicationRepository applications = mock(RoleApplicationRepository.class);
//    CatalogClient catalog = mock(CatalogClient.class);
//    UUID userId = UUID.randomUUID();
//    UUID applicationId = UUID.randomUUID();
//    UUID adminId = UUID.randomUUID();
//    User user = new User();
//    user.setId(userId);
//    user.setRole("CUSTOMER");
//    RoleApplication application = new RoleApplication();
//    application.setId(applicationId);
//    application.setUserId(userId);
//    application.setRequestedRole("RESTAURANT_OWNER");
//    application.setRestaurantName("Pavan Kitchen");
//    application.setStatus("PENDING");
//    when(applications.findById(applicationId)).thenReturn(Optional.of(application));
//    when(users.findById(userId)).thenReturn(Optional.of(user));
//    when(applications.save(any(RoleApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
//
//    RoleApplication approved = service(users, mock(PasswordEncoder.class), applications, catalog)
//        .approve(applicationId, adminId);
//
//    assertEquals("APPROVED", approved.getStatus());
//    assertEquals("RESTAURANT_OWNER", user.getRole());
//    verify(catalog).createRestaurant(adminId, "ADMIN",
//        java.util.Map.of("name", "Pavan Kitchen", "ownerId", userId));
//  }
//
//  @Test
//  void legacyAdministratorApplicationCannotGrantAnotherAdmin() {
//    UserRepository users = mock(UserRepository.class);
//    RoleApplicationRepository applications = mock(RoleApplicationRepository.class);
//    UUID applicationId = UUID.randomUUID();
//    RoleApplication application = new RoleApplication();
//    application.setId(applicationId);
//    application.setRequestedRole("ADMIN");
//    application.setStatus("PENDING");
//    when(applications.findById(applicationId)).thenReturn(Optional.of(application));
//
//    ResponseStatusException error = assertThrows(ResponseStatusException.class,
//        () -> service(users, mock(PasswordEncoder.class), applications).approve(applicationId, UUID.randomUUID()));
//
//    assertEquals(409, error.getStatusCode().value());
//    verify(users, never()).findById(any(UUID.class));
//    verify(users, never()).save(any(User.class));
//  }
//
//  @Test
//  void ownerCanRestoreRestaurantFromApprovedApplication() {
//    UserRepository users = mock(UserRepository.class);
//    RoleApplicationRepository applications = mock(RoleApplicationRepository.class);
//    CatalogClient catalog = mock(CatalogClient.class);
//    UUID ownerId = UUID.randomUUID();
//    User owner = new User();
//    owner.setId(ownerId);
//    owner.setRole("RESTAURANT_OWNER");
//    RoleApplication application = new RoleApplication();
//    application.setUserId(ownerId);
//    application.setRequestedRole("RESTAURANT_OWNER");
//    application.setRestaurantName("Munna Kitchen");
//    application.setStatus("APPROVED");
//    when(users.findById(ownerId)).thenReturn(Optional.of(owner));
//    when(applications.findByUserIdOrderByCreatedAtDesc(ownerId)).thenReturn(List.of(application));
//
//    service(users, mock(PasswordEncoder.class), applications, catalog).ensureOwnerRestaurant(ownerId);
//
//    verify(catalog).createRestaurant(ownerId, "ADMIN",
//        java.util.Map.of("name", "Munna Kitchen", "ownerId", ownerId));
//  }
//
//  @Test
//  void ownerCannotRestoreRestaurantWithoutApprovedOwnerApplication() {
//    UserRepository users = mock(UserRepository.class);
//    RoleApplicationRepository applications = mock(RoleApplicationRepository.class);
//    CatalogClient catalog = mock(CatalogClient.class);
//    UUID ownerId = UUID.randomUUID();
//    User owner = new User();
//    owner.setId(ownerId);
//    owner.setRole("RESTAURANT_OWNER");
//    when(users.findById(ownerId)).thenReturn(Optional.of(owner));
//    when(applications.findByUserIdOrderByCreatedAtDesc(ownerId)).thenReturn(List.of());
//
//    ResponseStatusException error = assertThrows(ResponseStatusException.class,
//        () -> service(users, mock(PasswordEncoder.class), applications, catalog)
//            .ensureOwnerRestaurant(ownerId));
//
//    assertEquals(409, error.getStatusCode().value());
//    verify(catalog, never()).createRestaurant(any(UUID.class), any(String.class), any());
//  }
//
//  @Test
//  void adminUserListingReturnsOnlySafeAccountFields() {
//    User user = new User();
//    user.setId(UUID.randomUUID());
//    user.setFullName("Customer");
//    user.setEmail("customer@example.com");
//    user.setRole("CUSTOMER");
//    user.setPasswordHash("secret-hash");
//    UserRepository users = mock(UserRepository.class);
//    when(users.findAll(any(org.springframework.data.domain.Pageable.class)))
//        .thenReturn(new PageImpl<>(List.of(user)));
//
//    var response = service(users, mock(PasswordEncoder.class), mock(RoleApplicationRepository.class))
//        .users(PageRequest.of(0, 10));
//
//    assertEquals(1, response.getTotalElements());
//    assertEquals("customer@example.com", response.getContent().get(0).email());
//    assertEquals("CUSTOMER", response.getContent().get(0).role());
//  }
//
//  private AuthService service(UserRepository users, PasswordEncoder encoder,
//      RoleApplicationRepository applications) {
//    return service(users, encoder, applications, mock(CatalogClient.class));
//  }
//
//  private AuthService service(UserRepository users, PasswordEncoder encoder,
//      RoleApplicationRepository applications, CatalogClient catalog) {
//    return new AuthService(users, encoder, applications, catalog, SECRET);
//  }
//}
