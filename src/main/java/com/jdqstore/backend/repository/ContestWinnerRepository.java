package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.ContestWinner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ContestWinnerRepository extends JpaRepository<ContestWinner, Long> {

    Optional<ContestWinner> findByContestId(Long contestId);

    @Modifying
    @Query("DELETE FROM ContestWinner w WHERE w.contest.id = :contestId")
    void deleteByContestId(@Param("contestId") Long contestId);
}
