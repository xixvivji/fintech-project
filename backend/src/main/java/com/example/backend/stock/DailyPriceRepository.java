package com.example.backend.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPriceRepository extends JpaRepository<DailyPriceEntity, Long> {
    Optional<DailyPriceEntity> findByCodeAndTradeDate(String code, LocalDate tradeDate);
    Optional<DailyPriceEntity> findTopByCodeOrderByTradeDateDesc(String code);
    Optional<DailyPriceEntity> findTopByCodeAndTradeDateLessThanEqualOrderByTradeDateDesc(String code, LocalDate tradeDate);
    @Query("select max(d.tradeDate) from DailyPriceEntity d where d.tradeDate <= :tradeDate")
    Optional<LocalDate> findLatestTradeDateOnOrBefore(@Param("tradeDate") LocalDate tradeDate);
    List<DailyPriceEntity> findByTradeDate(LocalDate tradeDate);
    List<DailyPriceEntity> findByCodeOrderByTradeDateAsc(String code);
    List<DailyPriceEntity> findByCodeAndTradeDateBetweenOrderByTradeDateAsc(String code, LocalDate startDate, LocalDate endDate);
    List<DailyPriceEntity> findByTradeDateOrderByVolumeDesc(LocalDate tradeDate);
}
