package com.foodflow.delivery.config;

import com.foodflow.delivery.domain.DeliveryPartner;
import com.foodflow.delivery.domain.DeliveryPartnerRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class SeedData {
    public static final UUID PARTNER_ONE_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final UUID PARTNER_TWO_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");

    @Bean
    ApplicationRunner seedDeliveryPartners(DeliveryPartnerRepository partners) {
        return args -> {
            if (partners.count() == 0) {
                DeliveryPartner first = new DeliveryPartner();
                first.setUserId(PARTNER_ONE_ID);
                first.setDisplayName("Demo Delivery Partner One");
                partners.save(first);
                DeliveryPartner second = new DeliveryPartner();
                second.setUserId(PARTNER_TWO_ID);
                second.setDisplayName("Demo Delivery Partner Two");
                partners.save(second);
            }
        };
    }
}
