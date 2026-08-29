package com.jdqstore.backend.dto;
import lombok.Builder;
import lombok.Data;
@Data
@Builder
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String role;
    private java.util.List<Long> storeIds;
}
