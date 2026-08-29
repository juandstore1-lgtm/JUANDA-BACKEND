package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.HomeCategoryCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HomeCategoryCollectionRepository extends JpaRepository<HomeCategoryCollection, Long> {
    List<HomeCategoryCollection> findAllByOrderByDisplayOrderAsc();
}
