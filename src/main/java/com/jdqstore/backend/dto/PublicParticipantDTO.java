package com.jdqstore.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.jdqstore.backend.entity.ContestParticipant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicParticipantDTO {
    private Long id;
    private String fullName;
    private String city;
    private String socialMedia;
    private String outfitImageUrl;

    public static PublicParticipantDTO fromEntity(ContestParticipant participant) {
        return PublicParticipantDTO.builder()
                .id(participant.getId())
                .fullName(participant.getFullName())
                .city(participant.getCity())
                .socialMedia(participant.getSocialMedia())
                .outfitImageUrl(participant.getOutfitImageUrl())
                .build();
    }
}
