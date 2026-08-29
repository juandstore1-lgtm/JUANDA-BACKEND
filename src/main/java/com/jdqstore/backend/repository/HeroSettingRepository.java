package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.HeroSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeroSettingRepository extends JpaRepository<HeroSetting, Long> {}
