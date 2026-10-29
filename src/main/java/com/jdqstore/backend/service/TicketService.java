package com.jdqstore.backend.service;

import com.jdqstore.backend.config.RafflePurchaseProperties;
import com.jdqstore.backend.dto.BuyerDTO;
import com.jdqstore.backend.dto.TicketDTO;
import com.jdqstore.backend.dto.TicketPurchaseRequest;
import com.jdqstore.backend.dto.TicketReservationDTO;
import com.jdqstore.backend.entity.Buyer;
import com.jdqstore.backend.entity.Raffle;
import com.jdqstore.backend.entity.Ticket;
import com.jdqstore.backend.repository.BuyerRepository;
import com.jdqstore.backend.repository.RaffleRepository;
import com.jdqstore.backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final BuyerRepository buyerRepository;
    private final RaffleRepository raffleRepository;
    private final RafflePurchaseProperties purchaseProperties;
    private final Clock clock;

    // Endpoint público: mismo formato, pero "buyer" siempre es null (sin datos personales del comprador)
    public List<TicketDTO> findByRaffleId(Long raffleId) {
        return ticketRepository.findAllByRaffleIdOrderByTicketNumberAsc(raffleId).stream()
                .map(ticket -> mapToDTO(ticket, false))
                .collect(Collectors.toList());
    }

    // Endpoint de administración (GLOBAL_ADMIN): incluye el comprador
    public List<TicketDTO> findByRaffleIdWithBuyers(Long raffleId) {
        return ticketRepository.findByRaffleIdOrderByTicketNumberAsc(raffleId).stream()
                .map(ticket -> mapToDTO(ticket, true))
                .collect(Collectors.toList());
    }

    /**
     * Reserva todos los boletos pedidos o ninguno. Los datos ya llegan validados y normalizados
     * (RafflePurchaseService); aquí se comprueban las reglas que dependen del estado guardado.
     */
    @Transactional
    public TicketReservationDTO purchaseTickets(Long raffleId, TicketPurchaseRequest request) {
        // Bloquea la rifa antes de leer los boletos: una compra concurrente espera al commit de esta y
        // luego ve los boletos ya reservados y el contador actualizado (evita doble venta y lost update)
        Raffle raffle = raffleRepository.findByIdForUpdate(raffleId).orElseThrow();
        Instant now = clock.instant();
        // Las reservas vencidas se liberan aquí también, sin esperar a la tarea programada
        releaseExpired(raffle, now);

        if (!"ACTIVE".equals(raffle.getStatus())) throw new IllegalStateException("La rifa no está disponible para la venta");

        // Un id inexistente se trata igual que uno de otra rifa (antes se ignoraba en silencio)
        List<Ticket> tickets = new ArrayList<>(ticketRepository.findAllById(request.getTicketIds()));
        if (tickets.size() != new HashSet<>(request.getTicketIds()).size()) throw new IllegalArgumentException("Ticket does not belong to this raffle");
        tickets.sort(Comparator.comparing(Ticket::getTicketNumber));
        for (Ticket t : tickets) {
            if (!t.getRaffle().getId().equals(raffleId)) throw new IllegalArgumentException("Ticket does not belong to this raffle");
            if (!"AVAILABLE".equals(t.getStatus())) throw new IllegalStateException("Ticket " + t.getTicketNumber() + " is no longer available");
        }

        String phone = request.getBuyer().getPhone();
        if (ticketRepository.countReservedByPhone(raffleId, phone) + tickets.size() > purchaseProperties.getMaxActiveTicketsPerPhone()) {
            throw new IllegalStateException("Se alcanzó el máximo de boletos reservados permitidos para este teléfono en esta rifa");
        }

        Buyer buyer = new Buyer();
        buyer.setName(request.getBuyer().getName());
        buyer.setPhone(phone);
        buyer.setEmail(request.getBuyer().getEmail());
        buyer.setPaymentStatus("PENDING");
        buyer = buyerRepository.save(buyer);

        // Reservado (no vendido) hasta que un administrador confirme el pago o venza la reserva
        Instant reservedUntil = now.plus(purchaseProperties.getReservationDuration());
        for (Ticket t : tickets) {
            t.setStatus("RESERVED");
            t.setBuyer(buyer);
            t.setReservationExpiresAt(reservedUntil);
        }

        raffle.setAvailableTickets(raffle.getAvailableTickets() - tickets.size());
        raffleRepository.save(raffle);
        ticketRepository.saveAll(tickets);

        TicketReservationDTO reservation = new TicketReservationDTO();
        reservation.setRaffleId(raffleId);
        reservation.setTicketIds(tickets.stream().map(Ticket::getId).collect(Collectors.toList()));
        reservation.setTicketNumbers(tickets.stream().map(Ticket::getTicketNumber).collect(Collectors.toList()));
        reservation.setStatus("RESERVED");
        reservation.setReservedUntil(reservedUntil);
        return reservation;
    }

    /** Libera las reservas vencidas de una rifa. Idempotente: con la rifa bloqueada vuelve a leer qué sigue RESERVED y vencido. */
    @Transactional
    public int releaseExpiredReservations(Long raffleId, Instant now) {
        return raffleRepository.findByIdForUpdate(raffleId).map(raffle -> releaseExpired(raffle, now)).orElse(0);
    }

    // Requiere la rifa bloqueada (findByIdForUpdate). Solo toca boletos RESERVED cuyo vencimiento ya pasó: los
    // aprobados (SOLD) o cancelados ya no cumplen la condición, así que una segunda ejecución no hace nada
    private int releaseExpired(Raffle raffle, Instant now) {
        List<Ticket> expired = ticketRepository.findExpiredReservations(raffle.getId(), now);
        for (Ticket t : expired) {
            t.setStatus("AVAILABLE");
            t.setBuyer(null);
            t.setReservationExpiresAt(null);
        }
        if (!expired.isEmpty()) {
            ticketRepository.saveAll(expired);
            restoreAvailability(raffle, expired.size());
        }
        return expired.size();
    }

    // Un boleto vuelve a estar libre: el contador sube una vez por boleto y nunca supera el total de la rifa
    private void restoreAvailability(Raffle raffle, int released) {
        raffle.setAvailableTickets(Math.min(raffle.getTotalTickets(), raffle.getAvailableTickets() + released));
        raffleRepository.save(raffle);
    }

    // Protege la acción del panel cuando su vista está desactualizada: si la reserva cambió de comprador
    // (venció y la tomó otra persona), no se aprueba ni se cancela la reserva equivocada
    private static void checkExpectedBuyer(Ticket ticket, Long expectedBuyerId) {
        if (expectedBuyerId != null && (ticket.getBuyer() == null || !expectedBuyerId.equals(ticket.getBuyer().getId()))) {
            throw new IllegalStateException("La reserva del boleto " + ticket.getTicketNumber() + " cambió. Recarga el panel e inténtalo de nuevo.");
        }
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

    // Llamada interna: necesita su propia transacción (el proxy no intercepta this.approveTicket(...))
    @Transactional
    public void approveTicket(Long ticketId) {
        approveTicket(ticketId, null);
    }

    /**
     * RESERVED -> SOLD (pago confirmado). Ya SOLD: no hace nada. AVAILABLE: error; antes quedaba SOLD sin comprador y
     * sin descontar el contador, y con reservas que vencen eso aprobaría un boleto liberado. El contador no cambia: se
     * descontó al reservar.
     */
    @Transactional
    public void approveTicket(Long ticketId, Long expectedBuyerId) {
        // Mismo bloqueo que purchaseTickets: sin el, una aprobacion que leyo el boleto antes de que una
        // compra hiciera commit sobrescribia la compra (boleto SOLD sin comprador)
        Long raffleId = ticketRepository.findRaffleIdById(ticketId).orElseThrow();
        raffleRepository.findByIdForUpdate(raffleId);
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        if ("SOLD".equals(ticket.getStatus())) return;
        if (!"RESERVED".equals(ticket.getStatus())) {
            throw new IllegalStateException("La reserva del boleto " + ticket.getTicketNumber() + " ya no está vigente");
        }
        checkExpectedBuyer(ticket, expectedBuyerId);
        if (ticket.getBuyer() != null) {
            Buyer buyer = ticket.getBuyer();
            buyer.setPaymentStatus("CONFIRMED");
            buyerRepository.save(buyer);
        }
        ticket.setStatus("SOLD");
        ticket.setReservationExpiresAt(null);
        ticketRepository.save(ticket);
    }

    @Transactional
    public void cancelTicket(Long ticketId) {
        cancelTicket(ticketId, null);
    }

    /**
     * RESERVED o SOLD -> AVAILABLE (se mantiene lo que permitía antes) y el boleto vuelve a contar como disponible una
     * sola vez. Ya AVAILABLE: no hace nada (antes tampoco cambiaba nada).
     */
    @Transactional
    public void cancelTicket(Long ticketId, Long expectedBuyerId) {
        // Mismo orden de bloqueos que la compra: ahora también actualiza el contador de la rifa
        Long raffleId = ticketRepository.findRaffleIdById(ticketId).orElseThrow();
        Optional<Raffle> raffle = raffleRepository.findByIdForUpdate(raffleId);
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        if ("AVAILABLE".equals(ticket.getStatus())) return;
        checkExpectedBuyer(ticket, expectedBuyerId);
        ticket.setBuyer(null);
        ticket.setStatus("AVAILABLE");
        ticket.setReservationExpiresAt(null);
        ticketRepository.save(ticket);
        raffle.ifPresent(r -> restoreAvailability(r, 1));
    }

    private TicketDTO mapToDTO(Ticket entity, boolean includeBuyer) {
        TicketDTO dto = new TicketDTO();
        dto.setId(entity.getId());
        dto.setRaffleId(entity.getRaffle().getId());
        dto.setTicketNumber(entity.getTicketNumber());
        dto.setStatus(entity.getStatus());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setReservedUntil(entity.getReservationExpiresAt());

        if (includeBuyer && entity.getBuyer() != null) {
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
