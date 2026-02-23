package com.example.backend.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimAccountRepository extends JpaRepository<SimAccountEntity, Long> {
    Optional<SimAccountEntity> findByUserId(Long userId);
}
