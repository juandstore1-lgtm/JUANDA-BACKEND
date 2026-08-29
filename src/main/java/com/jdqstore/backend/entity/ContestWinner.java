package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contest_winners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestWinner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "contest_id", nullable = false, unique = true)
    private Contest contest;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "participant_id", nullable = false)
    private ContestParticipant participant;

    private LocalDateTime selectedAt;

    @PrePersist
    protected void onCreate() {
        selectedAt = LocalDateTime.now();
    }
}
