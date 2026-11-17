package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findAllByOrderByCreatedAtDesc();
    List<JobApplication> findByVacancyIdOrderByCreatedAtDesc(Long vacancyId);
    List<JobApplication> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    long countByVacancyId(Long vacancyId);
    long countByEmployeeStatus(String employeeStatus);
}
