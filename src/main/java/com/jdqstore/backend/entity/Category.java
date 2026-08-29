package com.jdqstore.backend.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String image;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id")
    private Sede store;
}
