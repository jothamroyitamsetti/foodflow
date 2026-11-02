package com.foodflow.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtGatewayFilterTest {
  private static final String SECRET = "FoodFlowGatewayTestSecretKeyMustBeAtLeastThirtyTwoBytes";

  @Test
  void authenticatedOwnerRequestReceivesVerifiedIdentityHeaders() {
    UUID ownerId = UUID.randomUUID();
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/auth/applications/mine/restaurant")
            .header("Authorization", "Bearer " + token(ownerId, "RESTAURANT_OWNER"))
            .build());
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    new JwtGatewayFilter(SECRET).filter(exchange, request -> {
      forwarded.set(request);
      return Mono.empty();
    }).block();

    assertNotNull(forwarded.get());
    assertEquals(ownerId.toString(),
        forwarded.get().getRequest().getHeaders().getFirst("X-Auth-User-Id"));
    assertEquals("RESTAURANT_OWNER",
        forwarded.get().getRequest().getHeaders().getFirst("X-Auth-User-Role"));
  }

  @Test
  void protectedAuthEndpointRejectsRequestsWithoutToken() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/api/auth/applications/mine/restaurant").build());

    new JwtGatewayFilter(SECRET).filter(exchange, request -> Mono.empty()).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
  }

  @Test
  void protectedEndpointRejectsMalformedToken() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/orders")
            .header("Authorization", "Bearer not-a-jwt")
            .build());

    new JwtGatewayFilter(SECRET).filter(exchange, request -> Mono.empty()).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
  }

  @Test
  void publicEndpointDoesNotTrustClientIdentityHeaders() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/catalog/restaurants")
            .header("X-Auth-User-Id", UUID.randomUUID().toString())
            .header("X-Auth-User-Role", "ADMIN")
            .build());
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    new JwtGatewayFilter(SECRET).filter(exchange, request -> {
      forwarded.set(request);
      return Mono.empty();
    }).block();

    assertNotNull(forwarded.get());
    assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Auth-User-Id"));
    assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Auth-User-Role"));
  }

  @Test
  void ownerRestaurantLookupIsNotTreatedAsPublicCatalogRead() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/catalog/restaurants/mine").build());

    new JwtGatewayFilter(SECRET).filter(exchange, request -> Mono.empty()).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
  }

  @Test
  void ownerMenuLookupIsNotTreatedAsPublicRestaurantMenu() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/catalog/restaurants/mine/menu").build());

    new JwtGatewayFilter(SECRET).filter(exchange, request -> Mono.empty()).block();

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
  }

  @Test
  void menuForSpecificRestaurantRemainsPublic() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/catalog/restaurants/05435c1e-6996-4b7c-8a0b-fa1cb935d122/menu")
            .build());
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    new JwtGatewayFilter(SECRET).filter(exchange, request -> {
      forwarded.set(request);
      return Mono.empty();
    }).block();

    assertNotNull(forwarded.get());
    assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Auth-User-Id"));
  }

  @Test
  void publicRestaurantDiscoveryRemainsAccessibleWithoutToken() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/catalog/restaurants").build());
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    new JwtGatewayFilter(SECRET).filter(exchange, request -> {
      forwarded.set(request);
      return Mono.empty();
    }).block();

    assertNotNull(forwarded.get());
    assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Auth-User-Id"));
  }

  private String token(UUID userId, String role) {
    return Jwts.builder()
        .subject(userId.toString())
        .claim("role", role)
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
        .compact();
  }
}
