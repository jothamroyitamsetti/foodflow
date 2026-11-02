package com.foodflow.auth.service;

import com.foodflow.auth.entity.User;
import com.foodflow.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "foodflow.bootstrap-admin", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  @Value("${foodflow.bootstrap-admin.email}")
  private String email;

  @Value("${foodflow.bootstrap-admin.full-name}")
  private String fullName;

  @Value("${foodflow.bootstrap-admin.password}")
  private String password;

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    if (users.existsByRole("ADMIN")) {
      log.info("FoodFlow administrator already exists; bootstrap made no account changes");
      return;
    }
    if (email == null || email.isBlank() || fullName == null || fullName.isBlank()
        || password == null || password.length() < 8) {
      throw new IllegalStateException(
          "Admin bootstrap requires email, full-name, and a password of at least 8 characters");
    }

    String normalizedEmail = email.trim().toLowerCase();
    if (users.existsByEmail(normalizedEmail)) {
      throw new IllegalStateException(
          "Admin bootstrap email already belongs to a non-admin account; no role was changed");
    }

    User admin = new User();
    admin.setFullName(fullName.trim());
    admin.setEmail(normalizedEmail);
    admin.setPasswordHash(passwordEncoder.encode(password));
    admin.setRole("ADMIN");
    users.save(admin);
    log.info("Created the configured first FoodFlow administrator");
  }
}
