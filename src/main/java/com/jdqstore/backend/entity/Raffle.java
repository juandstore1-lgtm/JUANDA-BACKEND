package com.jdqstore.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "raffles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Raffle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false)
    private String prize;

    @Column(name = "ticket_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    @Column(name = "total_tickets", nullable = false)
    private Integer totalTickets;

    @Column(name = "available_tickets")
    private Integer availableTickets;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(nullable = false)
    @Builder.Default
    private String status = "UPCOMING"; // UPCOMING, ACTIVE, SOLD_OUT, FINISHED

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (availableTickets == null) availableTickets = totalTickets;
    }
}
