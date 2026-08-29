package com.jdqstore.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sedes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Sede {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    private String address;
    private String phone;
    private String schedule;
    @Column(columnDefinition = "TEXT")
    private String image;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
