package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.TikTokDayNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TikTokDayNoteRepository extends JpaRepository<TikTokDayNote, Long> {
    Optional<TikTokDayNote> findBySedeNameAndNoteDate(String sedeName, LocalDate noteDate);
    List<TikTokDayNote> findBySedeNameAndNoteDateBetween(String sedeName, LocalDate start, LocalDate end);
}
