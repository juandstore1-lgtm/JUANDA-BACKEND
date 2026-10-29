package com.jdqstore.backend.repository;
import com.jdqstore.backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    // Carga el comprador en la misma consulta (LEFT JOIN): TicketService lo mapea para cada boleto
    @EntityGraph(attributePaths = "buyer")
    List<Ticket> findByRaffleIdOrderByTicketNumberAsc(Long raffleId);
    // Vista pública: no lee los datos del comprador
    List<Ticket> findAllByRaffleIdOrderByTicketNumberAsc(Long raffleId);
    List<Ticket> findByRaffleIdAndStatus(Long raffleId, String status);

    @Query("SELECT t.raffle.id FROM Ticket t WHERE t.id = :id")
    Optional<Long> findRaffleIdById(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM Ticket t WHERE t.raffle.id = :raffleId")
    void deleteByRaffleId(@Param("raffleId") Long raffleId);

    // Reservas vencidas: la tarea programada solo las usa para saber qué rifas revisar; la liberación se hace
    // con la rifa bloqueada y vuelve a consultar el estado (findExpiredReservations)
    @Query("SELECT DISTINCT t.raffle.id FROM Ticket t WHERE t.status = 'RESERVED' AND t.reservationExpiresAt <= :now")
    List<Long> findRaffleIdsWithExpiredReservations(@Param("now") Instant now);

    @Query("SELECT t FROM Ticket t WHERE t.raffle.id = :raffleId AND t.status = 'RESERVED' AND t.reservationExpiresAt <= :now")
    List<Ticket> findExpiredReservations(@Param("raffleId") Long raffleId, @Param("now") Instant now);

    @Query("SELECT count(t) FROM Ticket t WHERE t.raffle.id = :raffleId AND t.status = 'RESERVED' AND t.buyer.phone = :phone")
    long countReservedByPhone(@Param("raffleId") Long raffleId, @Param("phone") String phone);
}
