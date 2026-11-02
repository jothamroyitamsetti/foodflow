package com.foodflow.catalog.config;

import com.foodflow.catalog.security.GatewayIdentityFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, GatewayIdentityFilter identity) throws Exception {
		return http.csrf(c -> c.disable()).cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a
						.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/api/auth/register",
								"/api/auth/login", "/api/catalog/restaurants/**", "/api/catalog/menu/**")
						.permitAll().anyRequest().authenticated())
				.addFilterBefore(identity, UsernamePasswordAuthenticationFilter.class).build();
	}
}
