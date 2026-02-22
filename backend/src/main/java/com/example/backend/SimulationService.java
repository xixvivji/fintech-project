package com.example.backend;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SimulationService {
    private static final double INITIAL_CASH = 10_000_000;
    private static final int REPLAY_TICK_SECONDS = 60;
    private static final int REPLAY_STEP_DAYS = 1;

    private final StockService stockService;
    private final Map<String, Position> positions = new HashMap<>();
    private final ScheduledExecutorService replayExecutor = Executors.newSingleThreadScheduledExecutor();

    private double cash = INITIAL_CASH;
    private double realizedPnl = 0;

    private LocalDate replayDate = null;
    private boolean replayRunning = false;

    public SimulationService(StockService stockService) {
        this.stockService = stockService;
    }

    @PostConstruct
    public void startReplayTicker() {
        replayExecutor.scheduleAtFixedRate(this::advanceReplayIfRunning, REPLAY_TICK_SECONDS, REPLAY_TICK_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdownReplayTicker() {
        replayExecutor.shutdownNow();
    }

    public synchronized PortfolioResponseDto getPortfolio() {
        LocalDate valuationDate = getValuationDate();
        List<HoldingDto> holdings = new ArrayList<>();
        double marketValue = 0;
        double unrealizedPnl = 0;

        for (Map.Entry<String, Position> entry : positions.entrySet()) {
            String code = entry.getKey();
            Position position = entry.getValue();
            double currentPrice = stockService.getClosePriceOnOrBefore(code, valuationDate);
            double value = currentPrice * position.quantity;
            double pnl = (currentPrice - position.avgPrice) * position.quantity;

            marketValue += value;
            unrealizedPnl += pnl;

            holdings.add(new HoldingDto(
                    code,
                    position.quantity,
                    round2(position.avgPrice),
                    round2(currentPrice),
                    round2(value),
                    round2(pnl)
            ));
        }

        holdings.sort(Comparator.comparing(HoldingDto::getCode));
        double totalValue = cash + marketValue;

        return new PortfolioResponseDto(
                round2(cash),
                round2(marketValue),
                round2(totalValue),
                round2(realizedPnl),
                round2(unrealizedPnl),
                holdings,
                valuationDate.toString(),
                replayRunning
        );
    }

    public synchronized SimOrderResponseDto placeMarketOrder(SimOrderRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("주문 요청이 비어 있습니다.");
        }

        String code = normalizeCode(request.getCode());
        String side = normalizeSide(request.getSide());
        int qty = request.getQuantity();

        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        LocalDate valuationDate = getValuationDate();
        double price = stockService.getClosePriceOnOrBefore(code, valuationDate);
        double amount = price * qty;
        long tradeAt = System.currentTimeMillis();

        if ("BUY".equals(side)) {
            if (cash < amount) {
                throw new IllegalArgumentException("잔고가 부족합니다.");
            }
            Position old = positions.get(code);
            if (old == null) {
                positions.put(code, new Position(qty, price));
            } else {
                int nextQty = old.quantity + qty;
                double nextAvg = ((old.avgPrice * old.quantity) + (price * qty)) / nextQty;
                old.quantity = nextQty;
                old.avgPrice = nextAvg;
            }
            cash -= amount;
        } else {
            Position old = positions.get(code);
            if (old == null || old.quantity < qty) {
                throw new IllegalArgumentException("매도 가능한 수량이 부족합니다.");
            }
            cash += amount;
            realizedPnl += (price - old.avgPrice) * qty;
            old.quantity -= qty;
            if (old.quantity == 0) {
                positions.remove(code);
            }
        }

        return new SimOrderResponseDto(
                code,
                side,
                qty,
                round2(price),
                round2(amount),
                round2(cash),
                tradeAt
        );
    }

    public synchronized PortfolioResponseDto startReplay(String startDate) {
        LocalDate date;
        if (startDate == null || startDate.isBlank()) {
            date = LocalDate.now().minusMonths(1);
        } else {
            try {
                date = LocalDate.parse(startDate.trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("startDate는 yyyy-MM-dd 형식이어야 합니다.");
            }
        }

        replayDate = date;
        replayRunning = true;
        return getPortfolio();
    }

    public synchronized PortfolioResponseDto pauseReplay() {
        replayRunning = false;
        return getPortfolio();
    }

    public synchronized ReplayStateDto getReplayState() {
        PortfolioResponseDto portfolio = getPortfolio();
        return new ReplayStateDto(
                portfolio.getValuationDate(),
                replayRunning,
                REPLAY_STEP_DAYS,
                REPLAY_TICK_SECONDS,
                portfolio
        );
    }

    public synchronized void reset() {
        positions.clear();
        cash = INITIAL_CASH;
        realizedPnl = 0;
        replayDate = null;
        replayRunning = false;
    }

    private void advanceReplayIfRunning() {
        synchronized (this) {
            if (!replayRunning || replayDate == null) return;
            replayDate = replayDate.plusDays(REPLAY_STEP_DAYS);
        }
    }

    private LocalDate getValuationDate() {
        return replayDate != null ? replayDate : LocalDate.now();
    }

    private String normalizeCode(String code) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다.");
        }
        return code.trim();
    }

    private String normalizeSide(String side) {
        if (side == null) {
            throw new IllegalArgumentException("주문 구분(side)은 필수입니다.");
        }
        String normalized = side.trim().toUpperCase();
        if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
            throw new IllegalArgumentException("side는 BUY 또는 SELL만 가능합니다.");
        }
        return normalized;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class Position {
        private int quantity;
        private double avgPrice;

        private Position(int quantity, double avgPrice) {
            this.quantity = quantity;
            this.avgPrice = avgPrice;
        }
    }
}
