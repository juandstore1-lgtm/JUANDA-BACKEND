package com.jdqstore.backend.repository;
import com.jdqstore.backend.entity.Raffle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RaffleRepository extends JpaRepository<Raffle, Long> {
}
