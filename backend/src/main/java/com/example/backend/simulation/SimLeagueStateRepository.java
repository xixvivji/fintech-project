package com.example.backend.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SimLeagueStateRepository extends JpaRepository<SimLeagueStateEntity, Long> {
    Optional<SimLeagueStateEntity> findByLeagueCode(String leagueCode);
}
