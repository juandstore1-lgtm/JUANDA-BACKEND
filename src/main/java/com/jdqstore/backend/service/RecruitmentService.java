package com.jdqstore.backend.service;

import com.jdqstore.backend.entity.JobApplication;
import com.jdqstore.backend.entity.JobInterview;
import com.jdqstore.backend.entity.JobVacancy;
import com.jdqstore.backend.repository.JobApplicationRepository;
import com.jdqstore.backend.repository.JobInterviewRepository;
import com.jdqstore.backend.repository.JobVacancyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecruitmentService {

    private final JobVacancyRepository vacancyRepository;
    private final JobApplicationRepository applicationRepository;
    private final JobInterviewRepository interviewRepository;

    // --- Métricas y Resumen ---
    public Map<String, Object> getSummaryMetrics() {
        long activeVacancies = vacancyRepository.countByStatus("ACTIVA");
        long totalApplications = applicationRepository.count();
        long scheduledInterviews = interviewRepository.countByStatus("PROGRAMADA");
        long hiredCount = applicationRepository.countByStatus("CONTRATADO");

        long enRevision = applicationRepository.countByStatus("EN_REVISION");
        long enEntrevista = applicationRepository.countByStatus("EN_ENTREVISTA");
        long preseleccionado = applicationRepository.countByStatus("PRESELECCIONADO");
        long seleccionado = applicationRepository.countByStatus("SELECCIONADO");
        long noSeleccionado = applicationRepository.countByStatus("NO_SELECCIONADO");

        long totalInterviews = interviewRepository.count();
        long interviewsPendientes = interviewRepository.countByStatus("PROGRAMADA");
        long interviewsRealizadas = interviewRepository.countByStatus("REALIZADA");
        long interviewsAprobadas = interviewRepository.countByResult("APROBADA");
        long interviewsNoAprobadas = interviewRepository.countByResult("NO_APROBADA");

        long hiredActivos = applicationRepository.countByEmployeeStatus("Activo");
        long hiredPeriodoPrueba = applicationRepository.countByEmployeeStatus("Periodo de prueba");
        long hiredInactivos = applicationRepository.countByEmployeeStatus("Inactivo");

        List<JobVacancy> allVacancies = vacancyRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> vacanciesWithCounts = allVacancies.stream().map(v -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", v.getId());
            map.put("title", v.getTitle());
            map.put("position", v.getPosition());
            map.put("sede", v.getSede());
            map.put("company", v.getCompany());
            map.put("status", v.getStatus());
            map.put("createdAt", v.getCreatedAt());
            map.put("postulacionesCount", applicationRepository.countByVacancyId(v.getId()));
            return map;
        }).collect(Collectors.toList());

        List<JobApplication> recentApps = applicationRepository.findAllByOrderByCreatedAtDesc().stream().limit(8).toList();
        List<JobInterview> upcomingInterviews = interviewRepository.findByStatusOrderByScheduledAtAsc("PROGRAMADA").stream().limit(5).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("activeVacancies", activeVacancies);
        result.put("totalApplications", totalApplications);
        result.put("scheduledInterviews", scheduledInterviews);
        result.put("hiredCount", hiredCount);

        result.put("enRevisionCount", enRevision);
        result.put("preseleccionadoCount", preseleccionado);
        result.put("enEntrevistaCount", enEntrevista);
        result.put("seleccionadoCount", seleccionado);
        result.put("noSeleccionadoCount", noSeleccionado);

        result.put("totalInterviews", totalInterviews);
        result.put("interviewsPendientes", interviewsPendientes);
        result.put("interviewsRealizadas", interviewsRealizadas);
        result.put("interviewsAprobadas", interviewsAprobadas);
        result.put("interviewsNoAprobadas", interviewsNoAprobadas);

        result.put("hiredActivos", hiredActivos);
        result.put("hiredPeriodoPrueba", hiredPeriodoPrueba);
        result.put("hiredInactivos", hiredInactivos);

        Map<String, Object> funnel = new HashMap<>();
        funnel.put("enRevision", enRevision);
        funnel.put("preseleccionado", preseleccionado);
        funnel.put("enEntrevista", enEntrevista);
        funnel.put("seleccionado", seleccionado);
        funnel.put("noSeleccionado", noSeleccionado);
        funnel.put("contratado", hiredCount);
        result.put("funnel", funnel);

        result.put("recentVacancies", vacanciesWithCounts);
        result.put("recentApplications", recentApps);
        result.put("upcomingInterviews", upcomingInterviews);

        return result;
    }

    // --- Vacantes ---
    public List<JobVacancy> getPublicActiveVacancies() {
        return vacancyRepository.findByStatusOrderByCreatedAtDesc("ACTIVA");
    }

    public List<JobVacancy> getAllVacanciesAdmin(String sede, String status, String search) {
        List<JobVacancy> list = vacancyRepository.findAllByOrderByCreatedAtDesc();
        return list.stream()
                .filter(v -> sede == null || sede.isEmpty() || "TODAS".equalsIgnoreCase(sede) 
                        || (v.getSede() != null && v.getSede().equalsIgnoreCase(sede)))
                .filter(v -> status == null || status.isEmpty() || "TODOS".equalsIgnoreCase(status) 
                        || (v.getStatus() != null && v.getStatus().equalsIgnoreCase(status)))
                .filter(v -> {
                    if (search == null || search.trim().isEmpty()) return true;
                    String s = search.toLowerCase();
                    return (v.getTitle() != null && v.getTitle().toLowerCase().contains(s))
                            || (v.getPosition() != null && v.getPosition().toLowerCase().contains(s))
                            || (v.getSede() != null && v.getSede().toLowerCase().contains(s));
                })
                .collect(Collectors.toList());
    }

    public JobVacancy getVacancyById(Long id) {
        return vacancyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Vacante no encontrada con ID: " + id));
    }

    @Transactional
    public JobVacancy createVacancy(JobVacancy vacancy) {
        if (vacancy.getStatus() == null || vacancy.getStatus().trim().isEmpty()) {
            vacancy.setStatus("ACTIVA");
        }
        return vacancyRepository.save(vacancy);
    }

    @Transactional
    public JobVacancy updateVacancy(Long id, JobVacancy updated) {
        JobVacancy existing = getVacancyById(id);
        existing.setCompany(updated.getCompany());
        existing.setSede(updated.getSede());
        existing.setPosition(updated.getPosition());
        existing.setEmploymentType(updated.getEmploymentType());
        existing.setTitle(updated.getTitle());
        existing.setDescription(updated.getDescription());
        existing.setWorkScheduleType(updated.getWorkScheduleType());
        existing.setWorkScheduleHours(updated.getWorkScheduleHours());
        existing.setSaturdayHours(updated.getSaturdayHours());
        existing.setWorkDays(updated.getWorkDays());
        existing.setSundayPolicy(updated.getSundayPolicy());
        existing.setSundayHours(updated.getSundayHours());
        existing.setHolidayPolicy(updated.getHolidayPolicy());
        existing.setHolidayHours(updated.getHolidayHours());
        existing.setScheduleObservations(updated.getScheduleObservations());
        existing.setRemunerationType(updated.getRemunerationType());
        existing.setBaseSalary(updated.getBaseSalary());
        existing.setTransportAid(updated.getTransportAid());
        existing.setSundayHolidaySurcharge(updated.getSundayHolidaySurcharge());
        existing.setBenefits(updated.getBenefits());
        existing.setSalaryObservations(updated.getSalaryObservations());
        existing.setRequirements(updated.getRequirements());
        existing.setContractType(updated.getContractType());
        existing.setPaymentFrequency(updated.getPaymentFrequency());
        existing.setPaymentDates(updated.getPaymentDates());
        existing.setProbationPeriod(updated.getProbationPeriod());
        existing.setWorkPlace(updated.getWorkPlace());
        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
        }
        if (updated.getVacanciesCount() != null) {
            existing.setVacanciesCount(updated.getVacanciesCount());
        }
        return vacancyRepository.save(existing);
    }

    @Transactional
    public JobVacancy updateVacancyStatus(Long id, String status) {
        JobVacancy existing = getVacancyById(id);
        existing.setStatus(status.toUpperCase());
        return vacancyRepository.save(existing);
    }

    @Transactional
    public void deleteVacancy(Long id) {
        vacancyRepository.deleteById(id);
    }

    // --- Postulaciones ---
    @Transactional
    public JobApplication submitApplication(JobApplication application) {
        if (application.getVacancyId() != null) {
            vacancyRepository.findById(application.getVacancyId()).ifPresent(v -> {
                application.setVacancyTitle(v.getTitle());
            });
        }
        application.setStatus("EN_REVISION");
        return applicationRepository.save(application);
    }

    public List<JobApplication> getApplicationsAdmin(Long vacancyId, String status, String search) {
        List<JobApplication> list = (vacancyId != null && vacancyId > 0)
                ? applicationRepository.findByVacancyIdOrderByCreatedAtDesc(vacancyId)
                : applicationRepository.findAllByOrderByCreatedAtDesc();

        return list.stream()
                .filter(a -> status == null || status.isEmpty() || "TODOS".equalsIgnoreCase(status) 
                        || (a.getStatus() != null && a.getStatus().equalsIgnoreCase(status)))
                .filter(a -> {
                    if (search == null || search.trim().isEmpty()) return true;
                    String s = search.toLowerCase();
                    return (a.getFullName() != null && a.getFullName().toLowerCase().contains(s))
                            || (a.getDocumentNumber() != null && a.getDocumentNumber().contains(s))
                            || (a.getEmail() != null && a.getEmail().toLowerCase().contains(s))
                            || (a.getTargetPosition() != null && a.getTargetPosition().toLowerCase().contains(s));
                })
                .collect(Collectors.toList());
    }

    public JobApplication getApplicationById(Long id) {
        return applicationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Postulación no encontrada con ID: " + id));
    }

    @Transactional
    public JobApplication updateApplicationStatus(Long id, String status, String notes) {
        JobApplication existing = getApplicationById(id);
        existing.setStatus(status.toUpperCase());
        if (notes != null && !notes.trim().isEmpty()) {
            existing.setAdminNotes(notes);
        }
        return applicationRepository.save(existing);
    }

    @Transactional
    public JobApplication rateCandidate(Long id, Integer score, String notes) {
        JobApplication app = getApplicationById(id);
        if (score != null) {
            app.setScore(score);
        }
        if (notes != null && !notes.trim().isEmpty()) {
            app.setAdminNotes(notes);
        }
        return applicationRepository.save(app);
    }

    @Transactional
    public JobApplication hireCandidate(Long id, Map<String, Object> data) {
        JobApplication app = getApplicationById(id);
        app.setStatus("CONTRATADO");
        app.setHiredAt(LocalDateTime.now());
        if (data.containsKey("employeeStatus") && data.get("employeeStatus") != null) {
            app.setEmployeeStatus(data.get("employeeStatus").toString());
        } else {
            app.setEmployeeStatus("Periodo de prueba");
        }
        if (data.containsKey("hiredPosition") && data.get("hiredPosition") != null) {
            app.setHiredPosition(data.get("hiredPosition").toString());
        }
        if (data.containsKey("hiredSede") && data.get("hiredSede") != null) {
            app.setHiredSede(data.get("hiredSede").toString());
        }
        if (data.containsKey("hiredSalary") && data.get("hiredSalary") != null) {
            app.setHiredSalary(data.get("hiredSalary").toString());
        }
        if (data.containsKey("hiredContractType") && data.get("hiredContractType") != null) {
            app.setHiredContractType(data.get("hiredContractType").toString());
        }
        return applicationRepository.save(app);
    }

    @Transactional
    public void deleteApplication(Long id) {
        applicationRepository.deleteById(id);
    }

    // --- Entrevistas ---
    @Transactional
    public JobInterview scheduleInterview(JobInterview interview) {
        if (interview.getApplicationId() != null) {
            JobApplication app = applicationRepository.findById(interview.getApplicationId()).orElse(null);
            if (app != null) {
                app.setStatus("EN_ENTREVISTA");
                app.setInterviewDate(interview.getScheduledAt());
                applicationRepository.save(app);

                if (interview.getCandidateName() == null) interview.setCandidateName(app.getFullName());
                if (interview.getCandidateDocument() == null) interview.setCandidateDocument(app.getDocumentNumber());
                if (interview.getCandidatePhone() == null) interview.setCandidatePhone(app.getPhone());
                if (interview.getPosition() == null) interview.setPosition(app.getTargetPosition());
                if (interview.getVacancyId() == null) interview.setVacancyId(app.getVacancyId());
            }
        }
        if (interview.getStatus() == null) interview.setStatus("PROGRAMADA");
        return interviewRepository.save(interview);
    }

    public List<JobInterview> getAllInterviews(String status) {
        if (status != null && !status.isEmpty() && !"TODAS".equalsIgnoreCase(status)) {
            return interviewRepository.findByStatusOrderByScheduledAtAsc(status);
        }
        return interviewRepository.findAllByOrderByScheduledAtAsc();
    }

    @Transactional
    public JobInterview updateInterviewStatus(Long id, String status) {
        JobInterview interview = interviewRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Entrevista no encontrada con ID: " + id));
        interview.setStatus(status.toUpperCase());
        return interviewRepository.save(interview);
    }

    @Transactional
    public JobInterview recordInterviewResult(Long id, String result, String notes) {
        JobInterview interview = interviewRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Entrevista no encontrada con ID: " + id));
        interview.setStatus("REALIZADA");
        interview.setResult(result != null ? result.toUpperCase() : "APROBADA");
        if (notes != null) {
            interview.setNotes(notes);
        }

        if (interview.getApplicationId() != null) {
            JobApplication app = applicationRepository.findById(interview.getApplicationId()).orElse(null);
            if (app != null) {
                if ("APROBADA".equalsIgnoreCase(result)) {
                    app.setStatus("SELECCIONADO");
                } else if ("NO_APROBADA".equalsIgnoreCase(result)) {
                    app.setStatus("NO_SELECCIONADO");
                }
                applicationRepository.save(app);
            }
        }
        return interviewRepository.save(interview);
    }

    @Transactional
    public void deleteInterview(Long id) {
        interviewRepository.deleteById(id);
    }
}
