package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "contest_participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContestParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    private String fullName;

    private String identificationNumber;

    private String phone;

    private String email;

    private String city;

    private String socialMedia;

    @Column(columnDefinition = "TEXT")
    private String outfitImageUrl;

    private Boolean acceptedTerms;

    private Boolean agreesToPublicDisplay;

    @Enumerated(EnumType.STRING)
    private ParticipantStatus status;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ParticipantStatus.PARTICIPANT;
        }
    }

    public enum ParticipantStatus {
        PARTICIPANT,
        WINNER,
        DISQUALIFIED
    }
}
