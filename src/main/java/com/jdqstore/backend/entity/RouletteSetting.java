package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roulette_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouletteSetting {
    @Id
    private Long id;
    
    @Column(name = "active_days")
    private String activeDays;
    
    @Column(name = "roulette_values")
    private String values;
    
    private String probabilities;
}
