package com.foodflow.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

	private static final String USER_ID_HEADER = "X-Auth-User-Id";
	private static final String USER_ROLE_HEADER = "X-Auth-User-Role";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final Set<String> ROLES = Set.of("ADMIN", "CUSTOMER", "RESTAURANT_OWNER", "DELIVERY_PARTNER");

	private final SecretKey signingKey;

	public JwtGatewayFilter(@Value("${foodflow.jwt.secret}") String secret) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerWebExchange cleanExchange = removeClientIdentityHeaders(exchange);
		ServerHttpRequest request = cleanExchange.getRequest();

		if (isPreflightRequest(request) || isPublicRoute(request)) {
			return chain.filter(cleanExchange);
		}

		String token = getBearerToken(request);
		if (token == null) {
			return unauthorized(exchange);
		}

		try {
			Claims claims = readClaims(token);
			String userId = claims.getSubject();
			String role = claims.get("role", String.class);
			validateIdentity(userId, role);

			ServerWebExchange authenticatedExchange = addVerifiedIdentityHeaders(cleanExchange, userId, role);
			return chain.filter(authenticatedExchange);
		} catch (JwtException | IllegalArgumentException exception) {
			return unauthorized(exchange);
		}
	}

	private ServerWebExchange removeClientIdentityHeaders(ServerWebExchange exchange) {
		ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
			headers.remove(USER_ID_HEADER);
			headers.remove(USER_ROLE_HEADER);
		}).build();
		return exchange.mutate().request(request).build();
	}

	private boolean isPreflightRequest(ServerHttpRequest request) {
		return request.getMethod() == HttpMethod.OPTIONS;
	}

	private boolean isPublicRoute(ServerHttpRequest request) {
		HttpMethod method = request.getMethod();
		String path = request.getURI().getPath();
		return isPublicAuthEndpoint(method, path) || isPublicCatalogRead(method, path) || isPublicDocumentation(path);
	}

	private boolean isPublicAuthEndpoint(HttpMethod method, String path) {
		return method == HttpMethod.POST && (path.equals("/api/auth/register") || path.equals("/api/auth/login"));
	}

	private boolean isPublicCatalogRead(HttpMethod method, String path) {
		if (method != HttpMethod.GET) {
			return false;
		}

		return path.equals("/api/catalog/restaurants") || path.matches("/api/catalog/restaurants/[0-9a-fA-F-]{36}/menu")
				|| path.equals("/api/catalog/menu") || path.matches("/api/catalog/menu/[^/]+");
	}

	private boolean isPublicDocumentation(String path) {
		return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.endsWith("/v3/api-docs")
				|| path.equals("/swagger-ui.html");
	}

	private String getBearerToken(ServerHttpRequest request) {
		String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			return null;
		}
		return authorization.substring(BEARER_PREFIX.length());
	}

	private Claims readClaims(String token) {
		var parsedToken = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);

		if (!Jwts.SIG.HS256.getId().equals(parsedToken.getHeader().getAlgorithm())) {
			throw new IllegalArgumentException("Unsupported JWT algorithm");
		}
		return parsedToken.getPayload();
	}

	private void validateIdentity(String userId, String role) {
		if (userId == null || role == null || !ROLES.contains(role)) {
			throw new IllegalArgumentException("JWT is missing a valid user identity");
		}
		UUID.fromString(userId);
	}

	private ServerWebExchange addVerifiedIdentityHeaders(ServerWebExchange exchange, String userId, String role) {
		ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
			headers.set(USER_ID_HEADER, userId);
			headers.set(USER_ROLE_HEADER, role);
		}).build();
		return exchange.mutate().request(request).build();
	}

	private Mono<Void> unauthorized(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		return exchange.getResponse().setComplete();
	}

	@Override
	public int getOrder() {
		return -100;
	}
}
