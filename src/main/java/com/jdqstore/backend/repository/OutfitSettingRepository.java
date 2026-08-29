package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.OutfitSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutfitSettingRepository extends JpaRepository<OutfitSetting, Long> {}
