package com.jdqstore.backend.repository;
import com.jdqstore.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
