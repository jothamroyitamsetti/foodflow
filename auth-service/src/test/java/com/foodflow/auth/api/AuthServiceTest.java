package com.foodflow.auth.api;

import com.foodflow.auth.user.UserAccount;
import com.foodflow.auth.user.UserAccountRepository;
import com.foodflow.auth.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthService service = new AuthService(users, encoder,
            "foodflow-local-development-secret-key-change-before-production", 60);

    @Test
    void registrationHashesPasswordAndCreatesCustomer() {
        when(users.existsByEmail("a@example.com")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        AuthDtos.UserResponse result = service.register(
                new AuthDtos.RegisterRequest("A@example.com", "Alice", "password123"));

        assertEquals("a@example.com", result.email());
        assertEquals(UserRole.CUSTOMER, result.role());
        verify(users).saveAndFlush(argThat(user -> "bcrypt-hash".equals(user.getPasswordHash())));
    }

    @Test
    void duplicateEmailIsRejected() {
        when(users.existsByEmail("a@example.com")).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.register(
                new AuthDtos.RegisterRequest("a@example.com", "Alice", "password123")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void loginReturnsSignedTokenForCorrectPassword() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("a@example.com");
        user.setFullName("Alice");
        user.setPasswordHash("hash");
        user.setRole(UserRole.CUSTOMER);
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("password123", "hash")).thenReturn(true);

        AuthDtos.TokenResponse result = service.login(new AuthDtos.LoginRequest("a@example.com", "password123"));

        assertEquals("Bearer", result.tokenType());
        assertFalse(result.accessToken().isBlank());
        assertEquals(user.getId(), result.user().id());
    }

    @Test
    void invalidCredentialsAreRejected() {
        when(users.findByEmail("a@example.com")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service.login(new AuthDtos.LoginRequest("a@example.com", "bad")));
    }
}
