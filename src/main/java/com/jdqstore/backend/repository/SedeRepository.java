package com.jdqstore.backend.repository;
import com.jdqstore.backend.entity.Sede;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SedeRepository extends JpaRepository<Sede, Long> {
    Optional<Sede> findFirstByBarrioIgnoreCase(String barrio);
    Optional<Sede> findFirstByNameIgnoreCase(String name);
}
