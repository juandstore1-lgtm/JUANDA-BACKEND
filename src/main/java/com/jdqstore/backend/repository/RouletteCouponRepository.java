package com.jdqstore.backend.repository;
import com.jdqstore.backend.entity.RouletteCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface RouletteCouponRepository extends JpaRepository<RouletteCoupon, Long> {
}
