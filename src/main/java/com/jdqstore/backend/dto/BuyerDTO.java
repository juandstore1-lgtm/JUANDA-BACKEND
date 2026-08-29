package com.jdqstore.backend.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BuyerDTO {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private String paymentStatus;
    private String paymentMethod;
    private String notes;
    private LocalDateTime createdAt;
}
