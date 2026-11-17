package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.TikTokScheduledVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TikTokScheduledVideoRepository extends JpaRepository<TikTokScheduledVideo, Long> {

    @Query("SELECT s FROM TikTokScheduledVideo s WHERE (:sedeName = 'TODAS' OR LOWER(s.sedeName) = LOWER(:sedeName)) " +
           "AND s.scheduledAt >= :start AND s.scheduledAt <= :end " +
           "ORDER BY s.scheduledAt ASC")
    List<TikTokScheduledVideo> findBySedeAndDateRange(
            @Param("sedeName") String sedeName,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<TikTokScheduledVideo> findBySedeNameOrderByScheduledAtAsc(String sedeName);
}
