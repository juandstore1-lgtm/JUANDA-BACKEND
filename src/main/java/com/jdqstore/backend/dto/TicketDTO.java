package com.jdqstore.backend.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
public class TicketDTO {
    private Long id;
    private Long raffleId;
    private String ticketNumber;
    private String status;
    private BuyerDTO buyer;
    private LocalDateTime updatedAt;
    // Solo en reservas con vencimiento: el resto de boletos mantiene exactamente el JSON anterior
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Instant reservedUntil;
}
