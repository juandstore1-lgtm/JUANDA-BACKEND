package com.jdqstore.backend.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RaffleDTO {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private String prize;
    private BigDecimal ticketPrice;
    private Integer totalTickets;
    private Integer availableTickets;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    private LocalDateTime createdAt;
}
