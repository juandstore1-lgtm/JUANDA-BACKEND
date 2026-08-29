package com.jdqstore.backend.dto;
import lombok.Data;
@Data
public class RouletteCouponDTO {
    private Long id;
    private String code;
    private java.math.BigDecimal discountPercentage;
    private java.time.LocalDateTime expiresAt;
    private Boolean isActive;
}
