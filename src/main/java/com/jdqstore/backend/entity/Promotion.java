package com.jdqstore.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 150)
    private String title;
    
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;
    
    @Column(name = "start_date")
    private LocalDateTime startDate;
    
    @Column(name = "end_date")
    private LocalDateTime endDate;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
