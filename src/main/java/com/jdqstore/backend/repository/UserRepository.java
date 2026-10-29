package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Se ejecuta en cada peticion autenticada (JwtAuthenticationFilter): trae el rol en la misma consulta
    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmail(String email);

    @Query("SELECT s.id FROM User u JOIN u.stores s WHERE u.id = :id")
    List<Long> findStoreIdsById(@Param("id") Long id);
}
