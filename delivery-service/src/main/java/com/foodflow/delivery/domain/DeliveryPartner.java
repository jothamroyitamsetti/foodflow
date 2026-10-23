package com.foodflow.delivery.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "delivery_partners")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryPartner {
    @Id
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false)
    private boolean available = true;
}
