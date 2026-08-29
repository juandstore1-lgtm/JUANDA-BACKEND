package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.ContestParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, Long> {

    List<ContestParticipant> findByContestIdOrderByIdDesc(Long contestId);

    List<ContestParticipant> findByContestIdAndAgreesToPublicDisplayTrueOrderByIdDesc(Long contestId);

    List<ContestParticipant> findByContestIdAndStatus(Long contestId, ContestParticipant.ParticipantStatus status);

    boolean existsByContestIdAndEmail(Long contestId, String email);

    boolean existsByContestIdAndPhone(Long contestId, String phone);

    boolean existsByContestIdAndIdentificationNumber(Long contestId, String identificationNumber);

    void deleteByContestId(Long contestId);
}
