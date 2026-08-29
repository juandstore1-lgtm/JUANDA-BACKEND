package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hero_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeroSetting {
    @Id
    private Long id;
    
    @Column(nullable = false, length = 1000)
    private String title;
    
    @Column(nullable = false, length = 2000)
    private String description;
    
    private String catalogLink;
    private String newDropLink;
}
