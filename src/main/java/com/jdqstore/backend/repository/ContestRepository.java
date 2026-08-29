package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.Contest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ContestRepository extends JpaRepository<Contest, Long> {

    Optional<Contest> findFirstByStatusOrderByIdDesc(Contest.ContestStatus status);

    Optional<Contest> findFirstByOrderByIdDesc();
}
