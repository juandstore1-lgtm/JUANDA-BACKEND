package com.jdqstore.backend.controller;

import com.jdqstore.backend.dto.TicketDTO;
import com.jdqstore.backend.dto.TicketPurchaseRequest;
import com.jdqstore.backend.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/raffles/{raffleId}/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<List<TicketDTO>> getTicketsByRaffle(@PathVariable Long raffleId) {
        return ResponseEntity.ok(ticketService.findByRaffleId(raffleId));
    }

    @PostMapping("/purchase")
    public ResponseEntity<Void> purchaseTickets(@PathVariable Long raffleId, @RequestBody TicketPurchaseRequest request) {
        ticketService.purchaseTickets(raffleId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{ticketId}/approve")
    public ResponseEntity<Void> approveTicket(@PathVariable Long raffleId, @PathVariable Long ticketId) {
        ticketService.approveTicket(ticketId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{ticketId}/cancel")
    public ResponseEntity<Void> cancelTicket(@PathVariable Long raffleId, @PathVariable Long ticketId) {
        ticketService.cancelTicket(ticketId);
        return ResponseEntity.ok().build();
    }
}
