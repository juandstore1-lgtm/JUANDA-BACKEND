package com.jdqstore.backend.dto;
import lombok.Data;
import java.time.Instant;
import java.util.List;

// Respuesta de la compra: qué boletos quedaron reservados y hasta cuándo. Sin datos del comprador
@Data
public class TicketReservationDTO {
    private Long raffleId;
    private List<Long> ticketIds;
    private List<String> ticketNumbers;
    private String status;
    private Instant reservedUntil;
}
