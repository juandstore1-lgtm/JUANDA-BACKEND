package com.jdqstore.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "roulette_coupons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RouletteCoupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
