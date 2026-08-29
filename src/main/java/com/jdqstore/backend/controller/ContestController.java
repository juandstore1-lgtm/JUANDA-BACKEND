package com.jdqstore.backend.controller;

import com.jdqstore.backend.dto.PublicParticipantDTO;
import com.jdqstore.backend.entity.Contest;
import com.jdqstore.backend.entity.ContestParticipant;
import com.jdqstore.backend.entity.ContestWinner;
import com.jdqstore.backend.repository.ContestParticipantRepository;
import com.jdqstore.backend.repository.ContestRepository;
import com.jdqstore.backend.repository.ContestWinnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestRepository contestRepository;
    private final ContestParticipantRepository participantRepository;
    private final ContestWinnerRepository winnerRepository;

    // --- PUBLIC ENDPOINTS ---

    @GetMapping("/public/active")
    public ResponseEntity<Contest> getActiveContest() {
        Optional<Contest> active = contestRepository.findFirstByStatusOrderByIdDesc(Contest.ContestStatus.ACTIVE);
        if (active.isPresent()) {
            return ResponseEntity.ok(active.get());
        }
        Optional<Contest> latest = contestRepository.findFirstByOrderByIdDesc();
        return ResponseEntity.ok(latest.orElse(null));
    }

    @PostMapping("/public/participate")
    public ResponseEntity<?> submitParticipation(@RequestBody ContestParticipant participant) {
        if (participant.getContest() == null || participant.getContest().getId() == null) {
            Optional<Contest> current = contestRepository.findFirstByOrderByIdDesc();
            if (current.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "No hay ningún concurso activo en este momento."));
            }
            participant.setContest(current.get());
        }

        Contest contest = contestRepository.findById(participant.getContest().getId()).orElse(null);
        if (contest == null || Boolean.FALSE.equals(contest.getFormEnabled())) {
            return ResponseEntity.badRequest().body(Map.of("message", "El formulario de participación no está habilitado actualmente."));
        }

        if (contest.getStatus() == Contest.ContestStatus.FINISHED || contest.getStatus() == Contest.ContestStatus.DISABLED) {
            return ResponseEntity.badRequest().body(Map.of("message", "Este concurso ya ha finalizado o se encuentra deshabilitado."));
        }

        // Validate duplicates safely
        boolean duplicateEmail = participant.getEmail() != null && participantRepository.existsByContestIdAndEmail(contest.getId(), participant.getEmail());
        boolean duplicatePhone = participant.getPhone() != null && participantRepository.existsByContestIdAndPhone(contest.getId(), participant.getPhone());
        boolean duplicateId = participant.getIdentificationNumber() != null && !participant.getIdentificationNumber().trim().isEmpty()
            && participantRepository.existsByContestIdAndIdentificationNumber(contest.getId(), participant.getIdentificationNumber());

        if (duplicateEmail || duplicatePhone || duplicateId) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya encontramos una participación registrada con este correo, teléfono o documento para este concurso."));
        }

        if (Boolean.FALSE.equals(participant.getAcceptedTerms())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debes aceptar los términos y condiciones del concurso para participar."));
        }

        participant.setStatus(ContestParticipant.ParticipantStatus.PARTICIPANT);
        ContestParticipant saved = participantRepository.save(participant);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/public/winner")
    public ResponseEntity<?> getPublicWinner(@RequestParam(required = false) Long contestId) {
        Long targetId = contestId;
        if (targetId == null) {
            Optional<Contest> activeOrLatest = contestRepository.findFirstByOrderByIdDesc();
            if (activeOrLatest.isPresent()) {
                targetId = activeOrLatest.get().getId();
            }
        }

        if (targetId == null) {
            return ResponseEntity.ok(null);
        }

        Optional<ContestWinner> winner = winnerRepository.findByContestId(targetId);
        return ResponseEntity.ok(winner.orElse(null));
    }

    @GetMapping("/public/participants")
    public ResponseEntity<List<PublicParticipantDTO>> getPublicParticipants(@RequestParam(required = false) Long contestId) {
        Long targetId = contestId;
        if (targetId == null) {
            Optional<Contest> activeOrLatest = contestRepository.findFirstByOrderByIdDesc();
            if (activeOrLatest.isPresent()) {
                targetId = activeOrLatest.get().getId();
            }
        }

        if (targetId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<ContestParticipant> publicParticipants = participantRepository.findByContestIdAndAgreesToPublicDisplayTrueOrderByIdDesc(targetId);
        List<PublicParticipantDTO> publicDTOs = publicParticipants.stream()
                .map(PublicParticipantDTO::fromEntity)
                .toList();

        return ResponseEntity.ok(publicDTOs);
    }

    // --- ADMIN ENDPOINTS ---

    @GetMapping("/admin/all")
    public ResponseEntity<List<Contest>> getAllContests() {
        return ResponseEntity.ok(contestRepository.findAll());
    }

    @PostMapping("/admin")
    public ResponseEntity<Contest> createContest(@RequestBody Contest contest) {
        if (contest.getStatus() == null) {
            contest.setStatus(Contest.ContestStatus.ACTIVE);
        }
        return ResponseEntity.ok(contestRepository.save(contest));
    }

    @PutMapping("/admin/{id}")
    public ResponseEntity<Contest> updateContest(@PathVariable Long id, @RequestBody Contest updated) {
        return contestRepository.findById(id).map(existing -> {
            existing.setTitle(updated.getTitle());
            existing.setDescription(updated.getDescription());
            existing.setRules(updated.getRules());
            existing.setBannerUrl(updated.getBannerUrl());
            existing.setStartDate(updated.getStartDate());
            existing.setEndDate(updated.getEndDate());
            existing.setStatus(updated.getStatus());
            existing.setShowInMenu(updated.getShowInMenu());
            existing.setFormEnabled(updated.getFormEnabled());
            existing.setCountdownEnabled(updated.getCountdownEnabled());
            existing.setClosedMessage(updated.getClosedMessage());
            existing.setRequireIdNumber(updated.getRequireIdNumber());
            return ResponseEntity.ok(contestRepository.save(existing));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/admin/{id}/participants")
    public ResponseEntity<List<ContestParticipant>> getParticipants(@PathVariable Long id) {
        return ResponseEntity.ok(participantRepository.findByContestIdOrderByIdDesc(id));
    }

    @PostMapping("/admin/{id}/select-winner")
    @Transactional
    public ResponseEntity<?> selectRandomWinner(@PathVariable Long id) {
        Contest contest = contestRepository.findById(id).orElse(null);
        if (contest == null) {
            return ResponseEntity.notFound().build();
        }

        // Get all valid participants
        List<ContestParticipant> validParticipants = participantRepository.findByContestIdAndStatus(
            id, ContestParticipant.ParticipantStatus.PARTICIPANT
        );

        if (validParticipants.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No hay participantes válidos elegibles para el sorteo en este concurso."));
        }

        // Random pick
        Random random = new Random();
        ContestParticipant selectedWinner = validParticipants.get(random.nextInt(validParticipants.size()));
        selectedWinner.setStatus(ContestParticipant.ParticipantStatus.WINNER);
        participantRepository.save(selectedWinner);

        // Record ContestWinner
        Optional<ContestWinner> existingWinner = winnerRepository.findByContestId(id);
        ContestWinner contestWinner;
        if (existingWinner.isPresent()) {
            contestWinner = existingWinner.get();
            contestWinner.setParticipant(selectedWinner);
            contestWinner.setSelectedAt(LocalDateTime.now());
        } else {
            contestWinner = ContestWinner.builder()
                .contest(contest)
                .participant(selectedWinner)
                .selectedAt(LocalDateTime.now())
                .build();
        }
        ContestWinner savedWinner = winnerRepository.save(contestWinner);

        // Update contest status to FINISHED
        contest.setStatus(Contest.ContestStatus.FINISHED);
        contest.setFormEnabled(false);
        contestRepository.save(contest);

        return ResponseEntity.ok(savedWinner);
    }

    @DeleteMapping("/admin/{id}/reset")
    @Transactional
    public ResponseEntity<?> resetContest(@PathVariable Long id) {
        Contest contest = contestRepository.findById(id).orElse(null);
        if (contest == null) {
            return ResponseEntity.notFound().build();
        }

        // Clean winner & participant records for referential integrity
        winnerRepository.deleteByContestId(id);
        participantRepository.deleteByContestId(id);

        // Reset contest state
        contest.setStatus(Contest.ContestStatus.ACTIVE);
        contest.setFormEnabled(true);
        contestRepository.save(contest);

        return ResponseEntity.ok(Map.of("message", "Concurso reseteado con éxito. Se eliminaron todos los participantes y registros asociados."));
    }
}
