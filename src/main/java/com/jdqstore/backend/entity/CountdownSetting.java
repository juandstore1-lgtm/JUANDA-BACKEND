package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "countdown_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountdownSetting {
    @Id
    private Long id;
    
    private String subtitle;
    
    @Column(nullable = false, length = 1000)
    private String title;
    
    private LocalDateTime targetDate;
    
    private String buttonText;
}
