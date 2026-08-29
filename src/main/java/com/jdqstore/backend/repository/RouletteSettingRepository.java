package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.RouletteSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RouletteSettingRepository extends JpaRepository<RouletteSetting, Long> {}
