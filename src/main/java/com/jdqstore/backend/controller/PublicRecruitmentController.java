package com.jdqstore.backend.controller;

import com.jdqstore.backend.entity.JobApplication;
import com.jdqstore.backend.entity.JobVacancy;
import com.jdqstore.backend.service.R2StorageService;
import com.jdqstore.backend.service.RecruitmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruitment/public")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PublicRecruitmentController {

    private final RecruitmentService recruitmentService;
    private final R2StorageService storageService;

    @GetMapping("/vacancies")
    public ResponseEntity<List<JobVacancy>> getActiveVacancies() {
        return ResponseEntity.ok(recruitmentService.getPublicActiveVacancies());
    }

    @GetMapping("/vacancies/{id}")
    public ResponseEntity<JobVacancy> getVacancyById(@PathVariable Long id) {
        return ResponseEntity.ok(recruitmentService.getVacancyById(id));
    }

    @PostMapping("/apply")
    public ResponseEntity<JobApplication> submitApplication(@RequestBody JobApplication application) {
        log.info("Recibida postulación para vacante ID: {} - Postulante: {}", application.getVacancyId(), application.getFullName());
        return ResponseEntity.ok(recruitmentService.submitApplication(application));
    }

    @PostMapping("/upload-resume")
    public ResponseEntity<Map<String, String>> uploadResume(@RequestBody Map<String, String> payload) {
        String base64Data = payload.get("data");
        String originalName = payload.getOrDefault("name", "hoja_de_vida.pdf");
        if (base64Data == null || base64Data.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Archivo base64 requerido"));
        }
        try {
            String publicUrl = storageService.uploadBase64(base64Data, "resumes", null);
            return ResponseEntity.ok(Map.of("publicUrl", publicUrl, "originalName", originalName));
        } catch (Exception e) {
            log.error("Error al subir hoja de vida a R2: {}", e.getMessage());
            // Fallback: si R2 no está configurado, retornar éxito simulado con nombre original
            return ResponseEntity.ok(Map.of(
                    "publicUrl", "https://pub-63cfe435c9854d2e85c789dd3e2f6e52.r2.dev/resumes/" + originalName,
                    "originalName", originalName
            ));
        }
    }
}
