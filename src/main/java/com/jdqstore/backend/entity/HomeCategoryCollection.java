package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "home_category_collections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class HomeCategoryCollection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    private String categoryFilter;

    private Integer displayOrder;
}
