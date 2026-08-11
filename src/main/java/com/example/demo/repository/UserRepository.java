package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    Page<User> findAllByTenantId(Long tenantId, Pageable pageable);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByRolesName(String roleName);
}