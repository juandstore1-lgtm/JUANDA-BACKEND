package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.CountdownSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CountdownSettingRepository extends JpaRepository<CountdownSetting, Long> {}
