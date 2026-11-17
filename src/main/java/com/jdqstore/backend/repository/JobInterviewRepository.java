package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.JobInterview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobInterviewRepository extends JpaRepository<JobInterview, Long> {
    List<JobInterview> findAllByOrderByScheduledAtAsc();
    List<JobInterview> findByStatusOrderByScheduledAtAsc(String status);
    long countByStatus(String status);
    long countByResult(String result);
}
