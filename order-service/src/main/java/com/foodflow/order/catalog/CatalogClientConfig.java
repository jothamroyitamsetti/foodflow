package com.foodflow.order.catalog;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogClientConfig {
    @Bean
    RequestInterceptor catalogInternalToken(@Value("${foodflow.internal-token}") String token) {
        return template -> template.header("X-FoodFlow-Internal-Token", token);
    }
}
