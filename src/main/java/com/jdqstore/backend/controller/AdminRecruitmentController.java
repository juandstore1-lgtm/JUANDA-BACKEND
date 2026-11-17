package com.jdqstore.backend.controller;

import com.jdqstore.backend.audit.Auditable;
import com.jdqstore.backend.entity.JobApplication;
import com.jdqstore.backend.entity.JobInterview;
import com.jdqstore.backend.entity.JobVacancy;
import com.jdqstore.backend.service.RecruitmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruitment/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminRecruitmentController {

    private final RecruitmentService recruitmentService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(recruitmentService.getSummaryMetrics());
    }

    // Vacantes
    @GetMapping("/vacancies")
    public ResponseEntity<List<JobVacancy>> getAllVacancies(
            @RequestParam(required = false) String sede,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(recruitmentService.getAllVacanciesAdmin(sede, status, search));
    }

    @GetMapping("/vacancies/{id}")
    public ResponseEntity<JobVacancy> getVacancyById(@PathVariable Long id) {
        return ResponseEntity.ok(recruitmentService.getVacancyById(id));
    }

    @PostMapping("/vacancies")
    @Auditable(action = "CREATE", entity = "RECRUITMENT_VACANCY")
    public ResponseEntity<JobVacancy> createVacancy(@RequestBody JobVacancy vacancy) {
        return ResponseEntity.ok(recruitmentService.createVacancy(vacancy));
    }

    @PutMapping("/vacancies/{id}")
    @Auditable(action = "UPDATE", entity = "RECRUITMENT_VACANCY")
    public ResponseEntity<JobVacancy> updateVacancy(@PathVariable Long id, @RequestBody JobVacancy vacancy) {
        return ResponseEntity.ok(recruitmentService.updateVacancy(id, vacancy));
    }

    @PatchMapping("/vacancies/{id}/status")
    @Auditable(action = "UPDATE_STATUS", entity = "RECRUITMENT_VACANCY")
    public ResponseEntity<JobVacancy> updateVacancyStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(recruitmentService.updateVacancyStatus(id, status));
    }

    @DeleteMapping("/vacancies/{id}")
    @Auditable(action = "DELETE", entity = "RECRUITMENT_VACANCY")
    public ResponseEntity<Map<String, String>> deleteVacancy(@PathVariable Long id) {
        recruitmentService.deleteVacancy(id);
        return ResponseEntity.ok(Map.of("message", "Vacante eliminada exitosamente"));
    }

    // Postulaciones
    @GetMapping("/applications")
    public ResponseEntity<List<JobApplication>> getApplications(
            @RequestParam(required = false) Long vacancyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(recruitmentService.getApplicationsAdmin(vacancyId, status, search));
    }

    @GetMapping("/applications/{id}")
    public ResponseEntity<JobApplication> getApplicationById(@PathVariable Long id) {
        return ResponseEntity.ok(recruitmentService.getApplicationById(id));
    }

    @PatchMapping("/applications/{id}/status")
    @Auditable(action = "UPDATE_STATUS", entity = "RECRUITMENT_APPLICATION")
    public ResponseEntity<JobApplication> updateApplicationStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String status = body.get("status");
        String notes = body.get("notes");
        return ResponseEntity.ok(recruitmentService.updateApplicationStatus(id, status, notes));
    }

    @PostMapping("/applications/{id}/score")
    @Auditable(action = "UPDATE", entity = "RECRUITMENT_APPLICATION")
    public ResponseEntity<JobApplication> rateCandidate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        Integer score = body.get("score") != null ? Integer.valueOf(body.get("score").toString()) : null;
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;
        return ResponseEntity.ok(recruitmentService.rateCandidate(id, score, notes));
    }

    @PostMapping("/applications/{id}/hire")
    @Auditable(action = "HIRE", entity = "RECRUITMENT_APPLICATION")
    public ResponseEntity<JobApplication> hireCandidate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.ok(recruitmentService.hireCandidate(id, body));
    }

    @DeleteMapping("/applications/{id}")
    @Auditable(action = "DELETE", entity = "RECRUITMENT_APPLICATION")
    public ResponseEntity<Map<String, String>> deleteApplication(@PathVariable Long id) {
        recruitmentService.deleteApplication(id);
        return ResponseEntity.ok(Map.of("message", "Postulación eliminada exitosamente"));
    }

    // Entrevistas
    @GetMapping("/interviews")
    public ResponseEntity<List<JobInterview>> getInterviews(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(recruitmentService.getAllInterviews(status));
    }

    @PostMapping("/interviews")
    @Auditable(action = "CREATE", entity = "RECRUITMENT_INTERVIEW")
    public ResponseEntity<JobInterview> scheduleInterview(@RequestBody JobInterview interview) {
        return ResponseEntity.ok(recruitmentService.scheduleInterview(interview));
    }

    @PatchMapping("/interviews/{id}/status")
    @Auditable(action = "UPDATE_STATUS", entity = "RECRUITMENT_INTERVIEW")
    public ResponseEntity<JobInterview> updateInterviewStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(recruitmentService.updateInterviewStatus(id, status));
    }

    @PostMapping("/interviews/{id}/result")
    @Auditable(action = "UPDATE_STATUS", entity = "RECRUITMENT_INTERVIEW")
    public ResponseEntity<JobInterview> recordInterviewResult(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String result = body.get("result");
        String notes = body.get("notes");
        return ResponseEntity.ok(recruitmentService.recordInterviewResult(id, result, notes));
    }

    @DeleteMapping("/interviews/{id}")
    @Auditable(action = "DELETE", entity = "RECRUITMENT_INTERVIEW")
    public ResponseEntity<Map<String, String>> deleteInterview(@PathVariable Long id) {
        recruitmentService.deleteInterview(id);
        return ResponseEntity.ok(Map.of("message", "Entrevista eliminada exitosamente"));
    }
}
