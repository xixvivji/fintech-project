package com.example.backend.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPriceRepository extends JpaRepository<DailyPriceEntity, Long> {
    Optional<DailyPriceEntity> findByCodeAndTradeDate(String code, LocalDate tradeDate);
    Optional<DailyPriceEntity> findTopByCodeOrderByTradeDateDesc(String code);
    Optional<DailyPriceEntity> findTopByCodeAndTradeDateLessThanEqualOrderByTradeDateDesc(String code, LocalDate tradeDate);
    List<DailyPriceEntity> findByCodeOrderByTradeDateAsc(String code);
    List<DailyPriceEntity> findByCodeAndTradeDateBetweenOrderByTradeDateAsc(String code, LocalDate startDate, LocalDate endDate);
}
