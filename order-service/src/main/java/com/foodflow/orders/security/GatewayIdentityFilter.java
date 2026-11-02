package com.foodflow.orders.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewayIdentityFilter extends OncePerRequestFilter {
  private static final Set<String> ROLES =
      Set.of("ADMIN", "CUSTOMER", "RESTAURANT_OWNER", "DELIVERY_PARTNER");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String userId = request.getHeader("X-Auth-User-Id");
    String role = request.getHeader("X-Auth-User-Role");
    if (userId != null || role != null) {
      try {
        UUID.fromString(userId);
        if (!ROLES.contains(role)) {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid gateway identity");
          return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (IllegalArgumentException | NullPointerException exception) {
        SecurityContextHolder.clearContext();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid gateway identity");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
