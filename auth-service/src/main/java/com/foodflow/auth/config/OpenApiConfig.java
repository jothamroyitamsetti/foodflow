package com.foodflow.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Makes Swagger Try-it-out calls go through the public API Gateway in local development. */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI foodFlowOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("FoodFlow API Gateway")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the access token from login without the Bearer prefix.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
