package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vacancyId;

    private String vacancyTitle;

    @Builder.Default
    private String status = "EN_REVISION"; // EN_REVISION, EN_ENTREVISTA, PRESELECCIONADO, CONTRATADO, NO_SELECCIONADO

    // Step 1: Datos personales y contacto
    private String fullName;

    private String documentType; // CC, CE, PASAPORTE

    private String documentNumber;

    private String phone;

    private String email;

    private String city;

    private String neighborhood;

    private String address;

    private String instagram;

    private String facebook;

    // Step 2: Experiencia y formación
    private String targetPosition;

    private String salesExperience; // Sin experiencia, Menos de 1 año, Entre 1 y 2 años, Más de 2 años

    private Boolean workedInClothingStores;

    @Column(columnDefinition = "TEXT")
    private String experienceDescription;

    private Boolean currentlyStudying;

    private String educationLevel; // Bachiller, Técnico, Tecnólogo, Profesional

    // Step 3: Horarios, pago y disponibilidad
    private String weekendHolidayAvailability; // Sí, tengo disponibilidad / Tengo disponibilidad parcial / No tengo disponibilidad

    private String agreesWithSchedule; // Sí, estoy de acuerdo / Deseo aclarar algunas condiciones / No estoy de acuerdo

    private String agreesWithPayment; // Sí, estoy de acuerdo / Necesito más información / No estoy de acuerdo

    private Boolean immediateAvailability;

    private String accessibleCommute; // Sí / Depende de la sede y el horario / Presentaría dificultades

    private String habitualTransport; // Moto propia, Carro propio, Transporte público / MIO, Bicicleta, Caminando, Otro

    // Step 4: Información adicional
    @Column(columnDefinition = "TEXT")
    private String motivation;

    @Column(columnDefinition = "TEXT")
    private String highlightSkills; // Comma-separated or JSON: "Servicio al cliente, Trabajo en equipo, etc."

    private Boolean socialMediaSalesExp;

    private String salesGoalsAchieved;

    @Column(columnDefinition = "TEXT")
    private String additionalObservations;

    // Step 5: Referencias y hoja de vida
    // Referencia familiar
    private String familyRefName;
    private String familyRefRelationship;
    private String familyRefPhone;

    // Referencia personal
    private String personalRefName;
    private String personalRefRelationship;
    private String personalRefPhone;

    // Referencia comercial / laboral
    private String commercialRefCompany;
    private String commercialRefPosition;
    private String commercialRefPhone;

    @Column(columnDefinition = "TEXT")
    private String resumeUrl;

    private String resumeOriginalName;

    // Step 6: Autorizaciones legales
    private Boolean dataPolicyAccepted;

    private Boolean infoVerificationAccepted;

    private Boolean creditCheckAccepted;

    // Historial, notas internas y proceso de selección
    private Integer score; // Puntaje 0 a 100

    private String employeeStatus; // Activo, Periodo de prueba, Inactivo

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime hiredAt;

    private String hiredPosition;

    private String hiredSede;

    private String hiredSalary;

    private String hiredContractType;

    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime interviewDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "EN_REVISION";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
