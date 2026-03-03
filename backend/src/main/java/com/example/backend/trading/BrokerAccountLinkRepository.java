package com.example.backend.trading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrokerAccountLinkRepository extends JpaRepository<BrokerAccountLinkEntity, Long> {
    Optional<BrokerAccountLinkEntity> findByUserIdAndProvider(Long userId, String provider);

    Optional<BrokerAccountLinkEntity> findByUserIdAndEnabledTrue(Long userId);
}
