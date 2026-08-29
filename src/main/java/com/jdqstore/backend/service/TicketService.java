package com.jdqstore.backend.service;

import com.jdqstore.backend.dto.BuyerDTO;
import com.jdqstore.backend.dto.TicketDTO;
import com.jdqstore.backend.dto.TicketPurchaseRequest;
import com.jdqstore.backend.entity.Buyer;
import com.jdqstore.backend.entity.Raffle;
import com.jdqstore.backend.entity.Ticket;
import com.jdqstore.backend.repository.BuyerRepository;
import com.jdqstore.backend.repository.RaffleRepository;
import com.jdqstore.backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final BuyerRepository buyerRepository;
    private final RaffleRepository raffleRepository;

    public List<TicketDTO> findByRaffleId(Long raffleId) {
        return ticketRepository.findByRaffleIdOrderByTicketNumberAsc(raffleId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void purchaseTickets(Long raffleId, TicketPurchaseRequest request) {
        Raffle raffle = raffleRepository.findById(raffleId).orElseThrow();
        
        Buyer buyer = new Buyer();
        buyer.setName(request.getBuyer().getName());
        buyer.setPhone(request.getBuyer().getPhone());
        buyer.setEmail(request.getBuyer().getEmail());
        buyer.setPaymentStatus("PENDING");
        buyer = buyerRepository.save(buyer);

        List<Ticket> tickets = ticketRepository.findAllById(request.getTicketIds());
        for (Ticket t : tickets) {
            if (!t.getRaffle().getId().equals(raffleId)) throw new IllegalArgumentException("Ticket does not belong to this raffle");
            if (!"AVAILABLE".equals(t.getStatus())) throw new IllegalStateException("Ticket " + t.getTicketNumber() + " is no longer available");
            
            t.setStatus("RESERVED"); // Initially reserved until payment confirmation
            t.setBuyer(buyer);
        }
        
        raffle.setAvailableTickets(raffle.getAvailableTickets() - tickets.size());
        raffleRepository.save(raffle);
        ticketRepository.saveAll(tickets);
    }

    @Transactional
    public void generateTicketsForRaffle(Raffle raffle) {
        int maxTicket = raffle.getTotalTickets() - 1;
        int length = String.valueOf(maxTicket).length();
        // Ensure at least 2 digits (e.g. 00 to 99)
        if (length < 2) length = 2;
        
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i <= maxTicket; i++) {
            Ticket ticket = new Ticket();
            ticket.setRaffle(raffle);
            ticket.setTicketNumber(String.format("%0" + length + "d", i));
            ticket.setStatus("AVAILABLE");
            tickets.add(ticket);
        }
        ticketRepository.saveAll(tickets);
    }

    @Transactional
    public void approveTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        if (ticket.getBuyer() != null) {
            Buyer buyer = ticket.getBuyer();
            buyer.setPaymentStatus("CONFIRMED");
            buyerRepository.save(buyer);
        }
        ticket.setStatus("SOLD");
        ticketRepository.save(ticket);
    }

    @Transactional
    public void cancelTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        if (ticket.getBuyer() != null) {
            ticket.setBuyer(null);
        }
        ticket.setStatus("AVAILABLE");
        ticketRepository.save(ticket);
    }

    private TicketDTO mapToDTO(Ticket entity) {
        TicketDTO dto = new TicketDTO();
        dto.setId(entity.getId());
        dto.setRaffleId(entity.getRaffle().getId());
        dto.setTicketNumber(entity.getTicketNumber());
        dto.setStatus(entity.getStatus());
        dto.setUpdatedAt(entity.getUpdatedAt());
        
        if (entity.getBuyer() != null) {
            BuyerDTO bDto = new BuyerDTO();
            bDto.setId(entity.getBuyer().getId());
            bDto.setName(entity.getBuyer().getName());
            bDto.setPhone(entity.getBuyer().getPhone());
            bDto.setEmail(entity.getBuyer().getEmail());
            bDto.setPaymentStatus(entity.getBuyer().getPaymentStatus());
            bDto.setPaymentMethod(entity.getBuyer().getPaymentMethod());
            bDto.setNotes(entity.getBuyer().getNotes());
            bDto.setCreatedAt(entity.getBuyer().getCreatedAt());
            dto.setBuyer(bDto);
        }
        return dto;
    }
}
