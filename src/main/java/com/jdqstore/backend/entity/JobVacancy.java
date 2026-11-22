package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_vacancies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobVacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String company; // "Juan'D Store" o "Quality Store"

    private String sede; // "República de Israel", "Villacolombia", etc.

    private String position; // "Asesor(a) de ventas", "Cajero(a)", etc.

    private String employmentType; // "Vinculación laboral directa"

    private String title; // "Asesor(a) de ventas - Tienda de ropa masculina"

    @Column(columnDefinition = "TEXT")
    private String description;

    // Horarios y descansos
    private String workScheduleType; // "Tiempo completo", "Medio tiempo", "Por turnos"

    private String workScheduleHours; // "10:00 a. m. - 8:00 p. m." (Lunes a viernes)

    private String saturdayHours; // "10:00 a. m. - 8:00 p. m." (Sábados)

    @Column(columnDefinition = "TEXT")
    private String workDays; // "Lun,Mar,Mié,Jue,Vie,Sáb" o fechas de temporada

    private String sundayPolicy; // "Domingos intercalados"

    private String sundayHours; // "10:00 a. m. - 8:00 p. m."

    private String holidayPolicy; // "Sí, se trabajan"

    private String holidayHours; // "10:00 a. m. - 8:00 p. m."

    @Column(columnDefinition = "TEXT")
    private String scheduleObservations;

    // Salario y beneficios
    private String remunerationType; // "Salario fijo", "Por comisión"

    private String baseSalary; // "$ 1.750.000"

    private String transportAid; // "$ 70.000"

    private String sundayHolidaySurcharge; // "$ 43.666"

    @Column(columnDefinition = "TEXT")
    private String benefits; // "Descuento en tienda, Incentivos por desempeño y metas"

    @Column(columnDefinition = "TEXT")
    private String salaryObservations;

    // Requisitos y condiciones
    @Column(columnDefinition = "TEXT")
    private String requirements; // JSON o lista separada por comas

    private String contractType; // "Vinculación laboral directa", "Término fijo"

    private String paymentFrequency; // "Quincenal", "Mensual"

    private String paymentDates; // "15 y 30/31 de cada mes"

    private String probationPeriod; // "Según normatividad vigente"

    private String workPlace; // "Sede asignada"

    @Builder.Default
    private String status = "ACTIVA"; // ACTIVA, EN_PROCESO, PAUSADA, CERRADA, BORRADOR

    @Builder.Default
    private Integer vacanciesCount = 1;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVA";
        if (vacanciesCount == null) vacanciesCount = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
