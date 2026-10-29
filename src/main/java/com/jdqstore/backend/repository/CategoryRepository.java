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

    @Query("SELECT s.id FROM Category c LEFT JOIN c.store s WHERE c.id = :id")
    Optional<Long> findStoreIdById(@Param("id") Long id);
}
