package com.jdqstore.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id", nullable = false)
    private Raffle raffle;

    @Column(name = "ticket_number", nullable = false)
    private String ticketNumber; // e.g. "001"

    @Column(nullable = false)
    @Builder.Default
    private String status = "AVAILABLE"; // AVAILABLE, RESERVED, SOLD

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private Buyer buyer;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Vencimiento de una reserva pendiente de pago (timestamptz). Null en boletos libres, vendidos y en las
    // reservas anteriores a esta columna, que no vencen automáticamente
    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @PreUpdate
    @PrePersist
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
