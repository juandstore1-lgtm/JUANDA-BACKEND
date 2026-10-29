package com.jdqstore.backend.controller;

import com.jdqstore.backend.dto.TicketDTO;
import com.jdqstore.backend.dto.TicketPurchaseRequest;
import com.jdqstore.backend.dto.TicketReservationDTO;
import com.jdqstore.backend.service.RafflePurchaseService;
import com.jdqstore.backend.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/raffles/{raffleId}/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final RafflePurchaseService purchaseService;

    @GetMapping
    public ResponseEntity<List<TicketDTO>> getTicketsByRaffle(@PathVariable Long raffleId) {
        return ResponseEntity.ok(ticketService.findByRaffleId(raffleId));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    public ResponseEntity<List<TicketDTO>> getTicketsWithBuyers(@PathVariable Long raffleId) {
        return ResponseEntity.ok(ticketService.findByRaffleIdWithBuyers(raffleId));
    }

    // Pública solo si app.raffle-purchase.public-enabled=true (SecurityConfig). Responde qué boletos quedaron
    // reservados y hasta cuándo; antes respondía 200 sin cuerpo
    @PostMapping("/purchase")
    public ResponseEntity<TicketReservationDTO> purchaseTickets(@PathVariable Long raffleId, @RequestBody TicketPurchaseRequest request,
                                                                HttpServletRequest httpRequest) {
        return ResponseEntity.ok(purchaseService.purchase(raffleId, request, httpRequest));
    }

    // buyerId (opcional): comprador que muestra el panel; si la reserva cambió de comprador, la acción se rechaza
    @PutMapping("/{ticketId}/approve")
    public ResponseEntity<Void> approveTicket(@PathVariable Long raffleId, @PathVariable Long ticketId,
                                              @RequestParam(required = false) Long buyerId) {
        ticketService.approveTicket(ticketId, buyerId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{ticketId}/cancel")
    public ResponseEntity<Void> cancelTicket(@PathVariable Long raffleId, @PathVariable Long ticketId,
                                             @RequestParam(required = false) Long buyerId) {
        ticketService.cancelTicket(ticketId, buyerId);
        return ResponseEntity.ok().build();
    }
}
