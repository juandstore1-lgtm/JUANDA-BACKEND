package com.jdqstore.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "buyers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Buyer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    private String email;

    @Column(name = "payment_status")
    private String paymentStatus; // PENDING, CONFIRMED, FAILED

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
