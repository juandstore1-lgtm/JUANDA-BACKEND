package com.jdqstore.backend.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TicketDTO {
    private Long id;
    private Long raffleId;
    private String ticketNumber;
    private String status;
    private BuyerDTO buyer;
    private LocalDateTime updatedAt;
}
