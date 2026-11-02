package com.foodflow.auth.service;

import com.foodflow.auth.entity.User;
import com.foodflow.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class AdminBootstrapRunnerTest {
  @Test
  void createsAnAdminOnlyWhenConfiguredAndNoAdminExists() throws Exception {
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(users.existsByRole("ADMIN")).thenReturn(false);
    when(users.existsByEmail("admin@example.com")).thenReturn(false);
    when(encoder.encode("a-long-test-password")).thenReturn("hashed-password");
    AdminBootstrapRunner runner = runner(users, encoder);
    setField(runner, "email", " Admin@Example.com ");
    setField(runner, "fullName", "FoodFlow Admin");
    setField(runner, "password", "a-long-test-password");

    runner.run(new DefaultApplicationArguments(new String[0]));

    verify(users).save(org.mockito.ArgumentMatchers.argThat(user ->
        "admin@example.com".equals(user.getEmail())
            && "ADMIN".equals(user.getRole())
            && "hashed-password".equals(user.getPasswordHash())));
  }

  @Test
  void acceptsPasswordAtTheSameMinimumLengthAsAccountRegistration() throws Exception {
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(users.existsByRole("ADMIN")).thenReturn(false);
    when(users.existsByEmail("admin@example.com")).thenReturn(false);
    when(encoder.encode("Admin@123")).thenReturn("hashed-password");
    AdminBootstrapRunner runner = runner(users, encoder);
    setField(runner, "email", "admin@example.com");
    setField(runner, "fullName", "FoodFlow Admin");
    setField(runner, "password", "Admin@123");

    runner.run(new DefaultApplicationArguments(new String[0]));

    verify(users).save(org.mockito.ArgumentMatchers.argThat(user ->
        "admin@example.com".equals(user.getEmail())
            && "ADMIN".equals(user.getRole())
            && "hashed-password".equals(user.getPasswordHash())));
  }

  @Test
  void neverChangesAnExistingCustomerIntoAnAdmin() throws Exception {
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(users.existsByRole("ADMIN")).thenReturn(false);
    when(users.existsByEmail("admin@example.com")).thenReturn(true);
    AdminBootstrapRunner runner = runner(users, encoder);
    setField(runner, "email", "admin@example.com");
    setField(runner, "fullName", "FoodFlow Admin");
    setField(runner, "password", "a-long-test-password");

    assertThrows(IllegalStateException.class,
        () -> runner.run(new DefaultApplicationArguments(new String[0])));
    verify(users, never()).save(any(User.class));
  }

  @Test
  void doesNotResetCredentialsWhenAnAdminAlreadyExists() throws Exception {
    UserRepository users = mock(UserRepository.class);
    AdminBootstrapRunner runner = runner(users, mock(PasswordEncoder.class));
    when(users.existsByRole("ADMIN")).thenReturn(true);

    runner.run(new DefaultApplicationArguments(new String[0]));

    verify(users, never()).save(any(User.class));
  }

  private AdminBootstrapRunner runner(UserRepository users, PasswordEncoder encoder) {
    return new AdminBootstrapRunner(users, encoder);
  }
}
