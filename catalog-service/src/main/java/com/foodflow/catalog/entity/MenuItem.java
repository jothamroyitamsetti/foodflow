package com.foodflow.catalog.entity;
import jakarta.persistence.*; import lombok.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="menu_items") @Getter @Setter @NoArgsConstructor
public class MenuItem { @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(name="restaurant_id") private UUID restaurantId; @Column(nullable=false) private String restaurantName; @Column(nullable=false) private String name; @Column(length=1000) private String description; @Column(nullable=false,precision=10,scale=2) private BigDecimal price; @Column(nullable=false) private int availableQuantity; @Column(nullable=false) private boolean available=true; @Version private Long version; }
