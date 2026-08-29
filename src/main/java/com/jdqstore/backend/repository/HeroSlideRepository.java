package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.HeroSlide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeroSlideRepository extends JpaRepository<HeroSlide, Long> {}
