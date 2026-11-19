package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.TikTokSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TikTokSettingsRepository extends JpaRepository<TikTokSettings, Long> {
}
