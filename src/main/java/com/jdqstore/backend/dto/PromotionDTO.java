package com.jdqstore.backend.dto;
import lombok.Data;
@Data
public class PromotionDTO {
    private Long id;
    private String title;
    private java.math.BigDecimal discountPercentage;
    private java.time.LocalDateTime startDate;
    private java.time.LocalDateTime endDate;
    private Boolean isActive;
}
