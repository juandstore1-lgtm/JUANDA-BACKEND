package com.jdqstore.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 150)
    private String name;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(name = "old_price", precision = 10, scale = 2)
    private BigDecimal oldPrice;
    
    @Column(name = "discount_percentage")
    private Integer discountPercentage;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    // Almacenamos el nombre de la categoria o un mapeo
    private String category;
    
    @ElementCollection
    @CollectionTable(name = "product_sizes", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "size")
    private List<String> sizes = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "product_colors", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "color")
    private List<String> colors = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", columnDefinition = "TEXT")
    private List<String> images = new ArrayList<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_stores",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "sede_id")
    )
    private List<Sede> stores = new ArrayList<>();
    
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "AVAILABLE"; // AVAILABLE | OUT_OF_STOCK
    
    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();
    
    @Column(name = "display_order")
    private Integer order;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
