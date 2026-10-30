package com.jdqstore.backend.repository;
import com.jdqstore.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByStoreId(Long storeId);

    // Listados sin la columna image (puede contener imágenes base64 de varios MB)
    @Query("SELECT c.id AS id, c.name AS name, c.store.id AS storeId FROM Category c")
    List<CategorySummary> findAllSummaries();

    @Query("SELECT c.id AS id, c.name AS name, c.store.id AS storeId FROM Category c WHERE c.store.id = :storeId")
    List<CategorySummary> findSummariesByStoreId(@Param("storeId") Long storeId);

    interface CategorySummary {
        Long getId();
        String getName();
        Long getStoreId();
    }

    @Query("SELECT s.id FROM Category c LEFT JOIN c.store s WHERE c.id = :id")
    Optional<Long> findStoreIdById(@Param("id") Long id);
}
