package com.jdqstore.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresignedUrlRequest {

    @NotBlank(message = "entity es obligatorio (ej: products, categories, sedes, raffles, contests, participants)")
    private String entity;

    @NotBlank(message = "contentType es obligatorio (ej: image/webp, image/jpeg, image/png)")
    private String contentType;

    private String originalFilename;

    private Long fileSize;
}
