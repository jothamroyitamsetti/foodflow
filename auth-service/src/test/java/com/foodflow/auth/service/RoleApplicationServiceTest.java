package com.foodflow.auth.service;

import com.foodflow.auth.client.CatalogClient;
import com.foodflow.auth.entity.RoleApplication;
import com.foodflow.auth.entity.User;
import com.foodflow.auth.repository.RoleApplicationRepository;
import com.foodflow.auth.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleApplicationServiceTest {
  private static final String SECRET = "FoodFlowLocalJwtSecretKeyMustBeAtLeastThirtyTwoCharsLong!";

  @Test
  void existingCustomerCanApplyAsRestaurantOwner() {
    UUID userId = UUID.randomUUID();
    User user = customer(userId);
    UserRepository users = mock(UserRepository.class);
    RoleApplicationRepository applications = mock(RoleApplicationRepository.class);
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(applications.existsByUserIdAndRequestedRoleAndStatus(userId, "RESTAURANT_OWNER", "PENDING"))
        .thenReturn(false);
    when(applications.save(any(RoleApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

    RoleApplication application = service(users, applications)
        .applyForRole(userId, "RESTAURANT_OWNER", "  Pavan Kitchen  ");

    assertEquals(userId, application.getUserId());
    assertEquals(user.getEmail(), application.getEmail());
    assertEquals(user.getFullName(), application.getFullName());
    assertEquals("RESTAURANT_OWNER", application.getRequestedRole());
    assertEquals("Pavan Kitchen", application.getRestaurantName());
    assertEquals("PENDING", application.getStatus());
    verify(applications).save(application);
  }

  @Test
  void existingCustomerCanApplyAsDeliveryPartnerWithoutRestaurantName() {
    UUID userId = UUID.randomUUID();
    UserRepository users = mock(UserRepository.class);
    RoleApplicationRepository applications = mock(RoleApplicationRepository.class);
    when(users.findById(userId)).thenReturn(Optional.of(customer(userId)));
    when(applications.existsByUserIdAndRequestedRoleAndStatus(userId, "DELIVERY_PARTNER", "PENDING"))
        .thenReturn(false);
    when(applications.save(any(RoleApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

    RoleApplication application = service(users, applications)
        .applyForRole(userId, "DELIVERY_PARTNER", null);

    assertEquals("DELIVERY_PARTNER", application.getRequestedRole());
    assertEquals(null, application.getRestaurantName());
    assertEquals("PENDING", application.getStatus());
  }

  @Test
  void restaurantOwnerApplicationRequiresRestaurantName() {
    UUID userId = UUID.randomUUID();
    UserRepository users = mock(UserRepository.class);
    RoleApplicationRepository applications = mock(RoleApplicationRepository.class);
    when(users.findById(userId)).thenReturn(Optional.of(customer(userId)));

    ResponseStatusException error = assertThrows(ResponseStatusException.class,
        () -> service(users, applications).applyForRole(userId, "RESTAURANT_OWNER", "  "));

    assertEquals(400, error.getStatusCode().value());
  }

  @Test
  void cannotSubmitDuplicatePendingApplication() {
    UUID userId = UUID.randomUUID();
    UserRepository users = mock(UserRepository.class);
    RoleApplicationRepository applications = mock(RoleApplicationRepository.class);
    when(users.findById(userId)).thenReturn(Optional.of(customer(userId)));
    when(applications.existsByUserIdAndRequestedRoleAndStatus(userId, "DELIVERY_PARTNER", "PENDING"))
        .thenReturn(true);

    ResponseStatusException error = assertThrows(ResponseStatusException.class,
        () -> service(users, applications).applyForRole(userId, "DELIVERY_PARTNER", null));

    assertEquals(409, error.getStatusCode().value());
  }

  private User customer(UUID userId) {
    User user = new User();
    user.setId(userId);
    user.setFullName("FoodFlow Customer");
    user.setEmail("customer@example.com");
    user.setRole("CUSTOMER");
    return user;
  }

  private AuthService service(UserRepository users, RoleApplicationRepository applications) {
    return new AuthService(users, mock(PasswordEncoder.class), applications, mock(CatalogClient.class), SECRET);
  }
}
