package com.jdqstore.backend.repository;
import com.jdqstore.backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByRaffleIdOrderByTicketNumberAsc(Long raffleId);
    List<Ticket> findByRaffleIdAndStatus(Long raffleId, String status);

    @Modifying
    @Query("DELETE FROM Ticket t WHERE t.raffle.id = :raffleId")
    void deleteByRaffleId(@Param("raffleId") Long raffleId);
}
