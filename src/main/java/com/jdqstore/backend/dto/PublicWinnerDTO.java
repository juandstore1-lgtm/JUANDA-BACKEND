package com.jdqstore.backend.dto;

import com.jdqstore.backend.entity.Contest;
import com.jdqstore.backend.entity.ContestWinner;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Mismo formato que ContestWinner, pero el participante solo con sus datos públicos (sin correo, teléfono ni documento). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicWinnerDTO {
    private Long id;
    private Contest contest;
    private PublicParticipantDTO participant;
    private LocalDateTime selectedAt;

    public static PublicWinnerDTO fromEntity(ContestWinner winner) {
        return PublicWinnerDTO.builder()
                .id(winner.getId())
                .contest(winner.getContest())
                .participant(PublicParticipantDTO.fromEntity(winner.getParticipant()))
                .selectedAt(winner.getSelectedAt())
                .build();
    }
}
