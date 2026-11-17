package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long applicationId;

    private Long vacancyId;

    private String candidateName;

    private String candidateDocument;

    private String candidatePhone;

    private String position;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime scheduledAt;

    @Builder.Default
    private String status = "PROGRAMADA"; // PROGRAMADA, REALIZADA, CANCELADA

    private String result; // APROBADA, NO_APROBADA, PENDIENTE

    private String candidateEmail;

    private String sede;

    private String interviewer;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PROGRAMADA";
    }
}
