package com.jdqstore.backend.repository;
import com.jdqstore.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("SELECT s.id FROM Product p JOIN p.stores s WHERE p.id = :id")
    List<Long> findStoreIdsById(@Param("id") Long id);
}
