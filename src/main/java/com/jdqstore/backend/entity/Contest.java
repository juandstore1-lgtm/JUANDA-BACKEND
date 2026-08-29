package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Entity
@Table(name = "contests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String rules;

    @Column(columnDefinition = "TEXT")
    private String bannerUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime startDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    private ContestStatus status;

    private Boolean showInMenu;

    private Boolean formEnabled;

    private Boolean countdownEnabled;

    @Column(columnDefinition = "TEXT")
    private String closedMessage;

    private Boolean requireIdNumber;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ContestStatus {
        DRAFT,
        UPCOMING,
        ACTIVE,
        FINISHED,
        DISABLED
    }
}
