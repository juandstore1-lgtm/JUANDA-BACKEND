package com.jdqstore.backend.repository;
import com.jdqstore.backend.entity.Raffle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RaffleRepository extends JpaRepository<Raffle, Long> {
    // SELECT ... FOR UPDATE: serializa las compras de una misma rifa hasta el commit
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Raffle r WHERE r.id = :id")
    Optional<Raffle> findByIdForUpdate(@Param("id") Long id);
}
