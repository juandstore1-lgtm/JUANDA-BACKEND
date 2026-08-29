package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "outfit_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutfitSetting {
    @Id
    private Long id;
    
    private String title;
    private String subtitle;
    
    @Column(length = 2000)
    private String description;
    
    private String badgeText;
    private String imageUrl;
    
    private String item1Name;
    private Double item1Price;
    
    private String item2Name;
    private Double item2Price;
    
    private String checkoutLink;
}
