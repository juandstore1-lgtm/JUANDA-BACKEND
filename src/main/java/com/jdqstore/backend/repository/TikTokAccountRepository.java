package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.TikTokAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TikTokAccountRepository extends JpaRepository<TikTokAccount, Long> {
    Optional<TikTokAccount> findBySedeName(String sedeName);
    Optional<TikTokAccount> findFirstBySedeNameIgnoreCase(String sedeName);
    Optional<TikTokAccount> findFirstBySedeNameIgnoreCaseAndStatus(String sedeName, String status);
    Optional<TikTokAccount> findBySedeId(Long sedeId);
    List<TikTokAccount> findByStatus(String status);
}
