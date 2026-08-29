package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hero_slides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeroSlide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String imageUrl;
    
    private String season;
    private String title;
    private Integer slideOrder;
}
