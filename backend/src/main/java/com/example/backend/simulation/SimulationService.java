package com.example.backend.simulation;

import com.example.backend.stock.StockService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SimulationService {
    private static final double INITIAL_CASH = 10_000_000;
    private static final long ACCOUNT_ID = 1L;
    private static final long REPLAY_STATE_ID = 1L;
    private static final int REPLAY_TICK_SECONDS = 60;
    private static final int REPLAY_STEP_DAYS = 1;

    private final StockService stockService;
    private final SimAccountRepository simAccountRepository;
    private final SimPositionRepository simPositionRepository;
    private final SimReplayStateRepository simReplayStateRepository;
    private final ScheduledExecutorService replayExecutor = Executors.newSingleThreadScheduledExecutor();

    public SimulationService(
            StockService stockService,
            SimAccountRepository simAccountRepository,
            SimPositionRepository simPositionRepository,
            SimReplayStateRepository simReplayStateRepository
    ) {
        this.stockService = stockService;
        this.simAccountRepository = simAccountRepository;
        this.simPositionRepository = simPositionRepository;
        this.simReplayStateRepository = simReplayStateRepository;
    }

    @PostConstruct
    public void startReplayTicker() {
        replayExecutor.scheduleAtFixedRate(this::advanceReplayIfRunning, REPLAY_TICK_SECONDS, REPLAY_TICK_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdownReplayTicker() {
        replayExecutor.shutdownNow();
    }

    @Transactional(readOnly = true)
    public synchronized PortfolioResponseDto getPortfolio() {
        SimAccountEntity account = getOrCreateAccount();
        SimReplayStateEntity replayState = getOrCreateReplayState();
        LocalDate valuationDate = replayState.getReplayDate() == null ? LocalDate.now() : LocalDate.parse(replayState.getReplayDate());

        List<HoldingDto> holdings = new ArrayList<>();
        double marketValue = 0;
        double unrealizedPnl = 0;

        for (SimPositionEntity position : simPositionRepository.findAll()) {
            double currentPrice = stockService.getClosePriceOnOrBefore(position.getCode(), valuationDate);
            double value = currentPrice * position.getQuantity();
            double pnl = (currentPrice - position.getAvgPrice()) * position.getQuantity();

            marketValue += value;
            unrealizedPnl += pnl;

            holdings.add(new HoldingDto(
                    position.getCode(),
                    position.getQuantity(),
                    round2(position.getAvgPrice()),
                    round2(currentPrice),
                    round2(value),
                    round2(pnl)
            ));
        }

        holdings.sort(Comparator.comparing(HoldingDto::getCode));
        double totalValue = account.getCash() + marketValue;

        return new PortfolioResponseDto(
                round2(account.getCash()),
                round2(marketValue),
                round2(totalValue),
                round2(account.getRealizedPnl()),
                round2(unrealizedPnl),
                holdings,
                valuationDate.toString(),
                replayState.isRunning()
        );
    }

    @Transactional
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

        SimAccountEntity account = getOrCreateAccount();
        SimReplayStateEntity replayState = getOrCreateReplayState();
        LocalDate valuationDate = replayState.getReplayDate() == null ? LocalDate.now() : LocalDate.parse(replayState.getReplayDate());

        double price = stockService.getClosePriceOnOrBefore(code, valuationDate);
        double amount = price * qty;
        long tradeAt = System.currentTimeMillis();

        if ("BUY".equals(side)) {
            if (account.getCash() < amount) {
                throw new IllegalArgumentException("잔고가 부족합니다.");
            }

            SimPositionEntity position = simPositionRepository.findById(code).orElse(null);
            if (position == null) {
                position = new SimPositionEntity();
                position.setCode(code);
                position.setQuantity(qty);
                position.setAvgPrice(price);
            } else {
                int nextQty = position.getQuantity() + qty;
                double nextAvg = ((position.getAvgPrice() * position.getQuantity()) + (price * qty)) / nextQty;
                position.setQuantity(nextQty);
                position.setAvgPrice(nextAvg);
            }
            simPositionRepository.save(position);
            account.setCash(account.getCash() - amount);
        } else {
            SimPositionEntity position = simPositionRepository.findById(code).orElse(null);
            if (position == null || position.getQuantity() < qty) {
                throw new IllegalArgumentException("매도 가능한 수량이 부족합니다.");
            }

            account.setCash(account.getCash() + amount);
            account.setRealizedPnl(account.getRealizedPnl() + ((price - position.getAvgPrice()) * qty));

            int remain = position.getQuantity() - qty;
            if (remain == 0) {
                simPositionRepository.delete(position);
            } else {
                position.setQuantity(remain);
                simPositionRepository.save(position);
            }
        }

        simAccountRepository.save(account);

        return new SimOrderResponseDto(
                code,
                side,
                qty,
                round2(price),
                round2(amount),
                round2(account.getCash()),
                tradeAt
        );
    }

    @Transactional
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

        SimReplayStateEntity replayState = getOrCreateReplayState();
        replayState.setReplayDate(date.toString());
        replayState.setRunning(true);
        simReplayStateRepository.save(replayState);

        return getPortfolio();
    }

    @Transactional
    public synchronized PortfolioResponseDto pauseReplay() {
        SimReplayStateEntity replayState = getOrCreateReplayState();
        replayState.setRunning(false);
        simReplayStateRepository.save(replayState);
        return getPortfolio();
    }

    @Transactional(readOnly = true)
    public synchronized ReplayStateDto getReplayState() {
        SimReplayStateEntity replayState = getOrCreateReplayState();
        PortfolioResponseDto portfolio = getPortfolio();
        return new ReplayStateDto(
                portfolio.getValuationDate(),
                replayState.isRunning(),
                REPLAY_STEP_DAYS,
                REPLAY_TICK_SECONDS,
                portfolio
        );
    }

    @Transactional
    public synchronized void reset() {
        SimAccountEntity account = getOrCreateAccount();
        account.setCash(INITIAL_CASH);
        account.setRealizedPnl(0);
        simAccountRepository.save(account);

        simPositionRepository.deleteAll();

        SimReplayStateEntity replayState = getOrCreateReplayState();
        replayState.setReplayDate(null);
        replayState.setRunning(false);
        simReplayStateRepository.save(replayState);
    }

    @Transactional
    protected synchronized void advanceReplayIfRunning() {
        SimReplayStateEntity replayState = getOrCreateReplayState();
        if (!replayState.isRunning() || replayState.getReplayDate() == null) {
            return;
        }
        LocalDate next = LocalDate.parse(replayState.getReplayDate()).plusDays(REPLAY_STEP_DAYS);
        replayState.setReplayDate(next.toString());
        simReplayStateRepository.save(replayState);
    }

    private SimAccountEntity getOrCreateAccount() {
        return simAccountRepository.findById(ACCOUNT_ID).orElseGet(() -> {
            SimAccountEntity entity = new SimAccountEntity();
            entity.setId(ACCOUNT_ID);
            entity.setCash(INITIAL_CASH);
            entity.setRealizedPnl(0);
            return simAccountRepository.save(entity);
        });
    }

    private SimReplayStateEntity getOrCreateReplayState() {
        return simReplayStateRepository.findById(REPLAY_STATE_ID).orElseGet(() -> {
            SimReplayStateEntity entity = new SimReplayStateEntity();
            entity.setId(REPLAY_STATE_ID);
            entity.setReplayDate(null);
            entity.setRunning(false);
            return simReplayStateRepository.save(entity);
        });
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
}
