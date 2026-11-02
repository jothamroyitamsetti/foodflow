package com.foodflow.auth.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="users", uniqueConstraints=@UniqueConstraint(columnNames="email")) @Getter @Setter @NoArgsConstructor
public class User { @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(nullable=false) private String fullName; @Column(nullable=false,unique=true) private String email; @Column(nullable=false) private String passwordHash; @Column(nullable=false) private String role="CUSTOMER"; @Column(nullable=false) private Instant createdAt=Instant.now(); }
