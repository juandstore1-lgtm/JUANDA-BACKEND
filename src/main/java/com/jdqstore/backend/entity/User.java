package com.jdqstore.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_sedes",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "sede_id")
    )
    private List<Sede> stores = new ArrayList<>();
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
