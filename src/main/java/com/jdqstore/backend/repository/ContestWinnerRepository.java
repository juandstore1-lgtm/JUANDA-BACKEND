package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.ContestWinner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ContestWinnerRepository extends JpaRepository<ContestWinner, Long> {

    Optional<ContestWinner> findByContestId(Long contestId);

    void deleteByContestId(Long contestId);
}
