package com.jdqstore.backend.service;

import com.jdqstore.backend.config.RafflePurchaseProperties;
import com.jdqstore.backend.exception.GlobalExceptionHandler;
import com.jdqstore.backend.repository.TicketRepository;
import com.jdqstore.backend.security.PurchaseRateLimiter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Libera las reservas vencidas. Cada rifa se procesa en su propia transacción con la rifa bloqueada (mismo orden que
 * compra, aprobación, cancelación y borrado), así que es segura con varias instancias o ejecuciones solapadas y tras
 * un reinicio: lo que quedó vencido se libera en la siguiente pasada. La compra también libera las vencidas de su rifa.
 */
@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final RafflePurchaseProperties properties;
    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final PurchaseRateLimiter rateLimiter;
    private final Clock clock;

    @Scheduled(fixedDelayString = "#{@rafflePurchaseProperties.expiryCheckInterval.toMillis()}",
            initialDelayString = "#{@rafflePurchaseProperties.expiryCheckInterval.toMillis()}")
    public void run() {
        if (!properties.isExpiryJobEnabled()) return;
        releaseExpiredReservations();
        rateLimiter.removeExpiredWindows();
    }

    /** Devuelve cuántos boletos liberó. */
    public int releaseExpiredReservations() {
        Instant now = clock.instant();
        int released = 0;
        for (Long raffleId : ticketRepository.findRaffleIdsWithExpiredReservations(now)) {
            try {
                released += ticketService.releaseExpiredReservations(raffleId, now);
            } catch (RuntimeException e) {
                // Se reintenta en la siguiente pasada; sin datos de compradores en el log
                log.warn("No se pudieron liberar las reservas vencidas de la rifa {}: {}", raffleId, GlobalExceptionHandler.describe(e));
            }
        }
        if (released > 0) log.info("Reservas vencidas liberadas: {} boletos", released);
        return released;
    }
}
