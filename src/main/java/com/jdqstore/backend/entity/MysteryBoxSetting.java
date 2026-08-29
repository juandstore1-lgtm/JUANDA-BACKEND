package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mystery_box_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MysteryBoxSetting {
    @Id
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Double price;

    private String estimatedValue;

    private String revealedSubtext;

    private String perk1;

    private String perk2;

    private String perk3;

    private String sizes;

    private Boolean active;
}
