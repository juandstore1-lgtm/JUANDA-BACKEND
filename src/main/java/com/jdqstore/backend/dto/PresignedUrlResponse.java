package com.jdqstore.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresignedUrlResponse {

    private String uploadUrl;
    private String publicUrl;
    private String key;
    private String contentType;
    private long expiresInSeconds;
}
