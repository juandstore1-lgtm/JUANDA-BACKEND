package com.jdqstore.backend.repository;

import com.jdqstore.backend.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
}
