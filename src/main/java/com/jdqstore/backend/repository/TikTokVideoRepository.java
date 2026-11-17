package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.TikTokVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TikTokVideoRepository extends JpaRepository<TikTokVideo, Long> {

    List<TikTokVideo> findBySedeNameOrderByPublishedAtDesc(String sedeName);

    List<TikTokVideo> findAllByOrderByPublishedAtDesc();

    Optional<TikTokVideo> findFirstByVideoId(String videoId);

    @Query("SELECT v FROM TikTokVideo v WHERE (:sedeName = 'TODAS' OR LOWER(v.sedeName) = LOWER(:sedeName)) " +
           "AND v.publishedAt >= :from AND v.publishedAt <= :to " +
           "ORDER BY v.publishedAt DESC")
    List<TikTokVideo> findFiltered(
            @Param("sedeName") String sedeName,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT COUNT(v) FROM TikTokVideo v WHERE (:sedeName IS NULL OR :sedeName = 'TODAS' OR LOWER(v.sedeName) = LOWER(:sedeName))")
    long countBySede(@Param("sedeName") String sedeName);

    @Query("SELECT COALESCE(SUM(v.viewCount), 0) FROM TikTokVideo v WHERE (:sedeName IS NULL OR :sedeName = 'TODAS' OR LOWER(v.sedeName) = LOWER(:sedeName))")
    long sumViewsBySede(@Param("sedeName") String sedeName);

    @Query("SELECT COALESCE(SUM(v.likeCount), 0) FROM TikTokVideo v WHERE (:sedeName IS NULL OR :sedeName = 'TODAS' OR LOWER(v.sedeName) = LOWER(:sedeName))")
    long sumLikesBySede(@Param("sedeName") String sedeName);

    @Query("SELECT COALESCE(SUM(v.commentCount), 0) FROM TikTokVideo v WHERE (:sedeName IS NULL OR :sedeName = 'TODAS' OR LOWER(v.sedeName) = LOWER(:sedeName))")
    long sumCommentsBySede(@Param("sedeName") String sedeName);

    @Query("SELECT COALESCE(SUM(v.shareCount), 0) FROM TikTokVideo v WHERE (:sedeName IS NULL OR :sedeName = 'TODAS' OR LOWER(v.sedeName) = LOWER(:sedeName))")
    long sumSharesBySede(@Param("sedeName") String sedeName);
}
