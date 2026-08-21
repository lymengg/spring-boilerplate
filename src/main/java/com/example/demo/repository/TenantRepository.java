package com.example.demo.repository;

import com.example.demo.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByName(String name);

    boolean existsByName(String name);

    Page<Tenant> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
