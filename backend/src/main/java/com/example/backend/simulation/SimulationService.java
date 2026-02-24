package com.example.backend.simulation;

import com.example.backend.auth.UserEntity;
import com.example.backend.auth.UserRepository;
import com.example.backend.stock.StockService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SimulationService {
    private static final double INITIAL_CASH = 50_000_000;
    private static final int REPLAY_TICK_SECONDS = 60;
    private static final int REPLAY_STEP_DAYS = 1;
    private static final LocalDate DEFAULT_REPLAY_START_DATE = LocalDate.of(2020, 1, 1);
    private static final String DEFAULT_LEAGUE_CODE = "MAIN";

    private final StockService stockService;
    private final SimAccountRepository simAccountRepository;
    private final SimPositionRepository simPositionRepository;
    private final SimReplayStateRepository simReplayStateRepository;
    private final SimPendingOrderRepository simPendingOrderRepository;
    private final SimTradeExecutionRepository simTradeExecutionRepository;
    private final SimLeagueStateRepository simLeagueStateRepository;
    private final UserRepository userRepository;
    private final ScheduledExecutorService replayExecutor = Executors.newSingleThreadScheduledExecutor();

    public SimulationService(
            StockService stockService,
            SimAccountRepository simAccountRepository,
            SimPositionRepository simPositionRepository,
            SimReplayStateRepository simReplayStateRepository,
            SimPendingOrderRepository simPendingOrderRepository,
            SimTradeExecutionRepository simTradeExecutionRepository,
            SimLeagueStateRepository simLeagueStateRepository,
            UserRepository userRepository
    ) {
        this.stockService = stockService;
        this.simAccountRepository = simAccountRepository;
        this.simPositionRepository = simPositionRepository;
        this.simReplayStateRepository = simReplayStateRepository;
        this.simPendingOrderRepository = simPendingOrderRepository;
        this.simTradeExecutionRepository = simTradeExecutionRepository;
        this.simLeagueStateRepository = simLeagueStateRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void startReplayTicker() {
        replayExecutor.scheduleAtFixedRate(this::advanceReplayIfRunning, REPLAY_TICK_SECONDS, REPLAY_TICK_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdownReplayTicker() {
        replayExecutor.shutdownNow();
    }

    @Transactional
    public synchronized PortfolioResponseDto getPortfolio(Long userId) {
        validateUserId(userId);
        SimAccountEntity account = getOrCreateAccount(userId);
        LocalDate valuationDate = resolveValuationDate(userId);

        List<HoldingDto> holdings = new ArrayList<>();
        double marketValue = 0;
        double unrealizedPnl = 0;

        for (SimPositionEntity position : simPositionRepository.findByUserId(userId)) {
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
                getOrCreateLeagueState().isRunning()
        );
    }

    @Transactional
    public synchronized SimOrderResponseDto placeOrder(Long userId, SimOrderRequestDto request) {
        validateUserId(userId);
        if (request == null) {
            throw new IllegalArgumentException("Order request is empty.");
        }

        String code = normalizeCode(request.getCode());
        String side = normalizeSide(request.getSide());
        String orderType = normalizeOrderType(request.getOrderType());
        int qty = request.getQuantity();
        Double limitPrice = normalizeLimitPrice(request.getLimitPrice(), orderType);

        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1.");
        }

        SimAccountEntity account = getOrCreateAccount(userId);
        LocalDate valuationDate = resolveValuationDate(userId);

        double price = stockService.getClosePriceOnOrBefore(code, valuationDate);
        if ("MARKET".equals(orderType) || isLimitExecutable(side, price, limitPrice)) {
            executeTrade(userId, account, code, side, qty, price);
            simAccountRepository.save(account);
            double amount = round2(price * qty);
            recordExecution(userId, code, side, orderType, limitPrice, qty, price, valuationDate);
            return new SimOrderResponseDto(
                    "FILLED",
                    "주문이 체결되었습니다.",
                    code,
                    side,
                    orderType,
                    limitPrice,
                    qty,
                    round2(price),
                    amount,
                    round2(account.getCash()),
                    System.currentTimeMillis()
            );
        }

        validatePendingOrderCanBePlaced(userId, account, code, side, qty, limitPrice);

        SimPendingOrderEntity pendingOrder = new SimPendingOrderEntity();
        pendingOrder.setUserId(userId);
        pendingOrder.setCode(code);
        pendingOrder.setSide(side);
        pendingOrder.setOrderType(orderType);
        pendingOrder.setLimitPrice(limitPrice);
        pendingOrder.setQuantity(qty);
        pendingOrder.setCreatedAt(System.currentTimeMillis());
        simPendingOrderRepository.save(pendingOrder);

        return new SimOrderResponseDto(
                "PENDING",
                "지정가 주문이 접수되었습니다. 조건 충족 시 자동 체결됩니다.",
                code,
                side,
                orderType,
                limitPrice,
                qty,
                null,
                null,
                round2(account.getCash()),
                pendingOrder.getCreatedAt()
        );
    }

    @Transactional
    public synchronized PortfolioResponseDto startReplay(Long userId, String startDate) {
        validateUserId(userId);

        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        if (leagueState.getAnchorDate() == null || leagueState.getAnchorDate().isBlank()) {
            LocalDate date = parseStartDateOrDefault(startDate);
            leagueState.setAnchorDate(date.toString());
            leagueState.setCurrentDate(date.toString());
        } else if (leagueState.getCurrentDate() == null || leagueState.getCurrentDate().isBlank()) {
            leagueState.setCurrentDate(leagueState.getAnchorDate());
        }
        leagueState.setRunning(true);
        simLeagueStateRepository.save(leagueState);

        SimReplayStateEntity replayState = getOrCreateReplayState(userId);
        replayState.setAnchorDate(leagueState.getAnchorDate());
        replayState.setReplayDate(leagueState.getCurrentDate());
        replayState.setRunning(true);
        simReplayStateRepository.save(replayState);

        processPendingOrders(userId, LocalDate.parse(leagueState.getCurrentDate()));
        return getPortfolio(userId);
    }

    @Transactional
    public synchronized PortfolioResponseDto pauseReplay(Long userId) {
        validateUserId(userId);

        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        leagueState.setRunning(false);
        simLeagueStateRepository.save(leagueState);

        SimReplayStateEntity replayState = getOrCreateReplayState(userId);
        replayState.setRunning(false);
        simReplayStateRepository.save(replayState);
        return getPortfolio(userId);
    }

    @Transactional
    public synchronized ReplayStateDto getReplayState(Long userId) {
        validateUserId(userId);
        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        PortfolioResponseDto portfolio = getPortfolio(userId);
        return new ReplayStateDto(
                portfolio.getValuationDate(),
                leagueState.getAnchorDate(),
                leagueState.isRunning(),
                REPLAY_STEP_DAYS,
                REPLAY_TICK_SECONDS,
                portfolio
        );
    }

    @Transactional
    public synchronized SimLeagueStateDto getLeagueState(Long userId) {
        validateUserId(userId);
        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        return new SimLeagueStateDto(
                DEFAULT_LEAGUE_CODE,
                leagueState.getAnchorDate(),
                leagueState.getCurrentDate(),
                leagueState.isRunning(),
                REPLAY_STEP_DAYS,
                REPLAY_TICK_SECONDS
        );
    }

    @Transactional(readOnly = true)
    public synchronized List<PendingOrderDto> getPendingOrders(Long userId) {
        validateUserId(userId);
        List<SimPendingOrderEntity> orders = simPendingOrderRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<PendingOrderDto> result = new ArrayList<>();
        for (SimPendingOrderEntity order : orders) {
            result.add(new PendingOrderDto(
                    order.getId(),
                    order.getCode(),
                    order.getSide(),
                    order.getOrderType(),
                    round2(order.getLimitPrice()),
                    order.getQuantity(),
                    order.getCreatedAt()
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public synchronized List<TradeExecutionDto> getTradeExecutions(Long userId) {
        validateUserId(userId);
        List<SimTradeExecutionEntity> rows = simTradeExecutionRepository.findByUserIdOrderByExecutedAtDesc(userId);
        List<TradeExecutionDto> result = new ArrayList<>();
        for (SimTradeExecutionEntity row : rows) {
            result.add(new TradeExecutionDto(
                    row.getId(),
                    row.getCode(),
                    row.getSide(),
                    row.getOrderType(),
                    row.getRequestedLimitPrice(),
                    row.getQuantity(),
                    round2(row.getPrice()),
                    round2(row.getAmount()),
                    row.getValuationDate(),
                    row.getExecutedAt()
            ));
        }
        return result;
    }

    @Transactional
    public synchronized List<SimRankingDto> getRankings(Long currentUserId) {
        validateUserId(currentUserId);
        List<UserEntity> users = userRepository.findAll();
        List<SimRankingRow> rows = new ArrayList<>();
        for (UserEntity user : users) {
            if (user.getId() == null) continue;
            PortfolioResponseDto p = getPortfolio(user.getId());
            double returnRate = ((p.getTotalValue() - INITIAL_CASH) / INITIAL_CASH) * 100.0;
            rows.add(new SimRankingRow(
                    user.getId(),
                    user.getName(),
                    round2(p.getTotalValue()),
                    round2(returnRate),
                    round2(p.getRealizedPnl()),
                    round2(p.getUnrealizedPnl()),
                    p.getValuationDate()
            ));
        }

        rows.sort(
                Comparator.comparingDouble(SimRankingRow::returnRate).reversed()
                        .thenComparingDouble(SimRankingRow::totalValue).reversed()
                        .thenComparingLong(SimRankingRow::userId)
        );

        List<SimRankingDto> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            SimRankingRow row = rows.get(i);
            result.add(new SimRankingDto(
                    i + 1,
                    row.userId(),
                    row.userName(),
                    row.totalValue(),
                    row.returnRate(),
                    row.realizedPnl(),
                    row.unrealizedPnl(),
                    row.valuationDate(),
                    row.userId().equals(currentUserId)
            ));
        }
        return result;
    }

    @Transactional
    public synchronized SimRankingPortfolioSummaryDto getRankingPortfolioSummary(Long currentUserId, Long targetUserId) {
        validateUserId(currentUserId);
        validateUserId(targetUserId);
        UserEntity user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        PortfolioResponseDto portfolio = getPortfolio(targetUserId);
        return new SimRankingPortfolioSummaryDto(user.getId(), user.getName(), portfolio);
    }

    @Transactional
    public synchronized void cancelPendingOrder(Long userId, Long orderId) {
        validateUserId(userId);
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("유효한 주문 ID가 필요합니다.");
        }
        SimPendingOrderEntity order = simPendingOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 미체결 주문이 없습니다."));
        if (!userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("본인 주문만 취소할 수 있습니다.");
        }
        simPendingOrderRepository.delete(order);
    }

    @Transactional
    public synchronized void reset(Long userId) {
        validateUserId(userId);
        SimAccountEntity account = getOrCreateAccount(userId);
        account.setCash(INITIAL_CASH);
        account.setRealizedPnl(0);
        simAccountRepository.save(account);

        simPositionRepository.deleteByUserId(userId);
        simPendingOrderRepository.deleteByUserId(userId);
        simTradeExecutionRepository.deleteByUserId(userId);

        SimReplayStateEntity replayState = getOrCreateReplayState(userId);
        replayState.setReplayDate(null);
        replayState.setAnchorDate(null);
        replayState.setRunning(false);
        simReplayStateRepository.save(replayState);
    }

    @Transactional
    protected synchronized void advanceReplayIfRunning() {
        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        if (!leagueState.isRunning() || leagueState.getCurrentDate() == null || leagueState.getCurrentDate().isBlank()) {
            return;
        }

        LocalDate next = LocalDate.parse(leagueState.getCurrentDate()).plusDays(REPLAY_STEP_DAYS);
        leagueState.setCurrentDate(next.toString());
        simLeagueStateRepository.save(leagueState);

        syncUserReplayDates(next);
        processPendingOrdersForAllUsers(next);
    }

    private LocalDate resolveValuationDate(Long userId) {
        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        if (leagueState.getCurrentDate() != null && !leagueState.getCurrentDate().isBlank()) {
            return LocalDate.parse(leagueState.getCurrentDate());
        }

        SimReplayStateEntity replayState = getOrCreateReplayState(userId);
        if (replayState.getReplayDate() != null && !replayState.getReplayDate().isBlank()) {
            return LocalDate.parse(replayState.getReplayDate());
        }
        return LocalDate.now();
    }

    private void syncUserReplayDates(LocalDate currentDate) {
        List<SimReplayStateEntity> states = simReplayStateRepository.findAll();
        for (SimReplayStateEntity state : states) {
            state.setReplayDate(currentDate.toString());
            state.setRunning(true);
        }
        if (!states.isEmpty()) {
            simReplayStateRepository.saveAll(states);
        }
    }

    private void processPendingOrdersForAllUsers(LocalDate valuationDate) {
        List<SimPendingOrderEntity> pendingOrders = simPendingOrderRepository.findAll();
        if (pendingOrders.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        for (SimPendingOrderEntity pendingOrder : pendingOrders) {
            if (pendingOrder.getUserId() != null) {
                userIds.add(pendingOrder.getUserId());
            }
        }
        for (Long userId : userIds) {
            processPendingOrders(userId, valuationDate);
        }
    }

    private SimLeagueStateEntity getOrCreateLeagueState() {
        return simLeagueStateRepository.findByLeagueCode(DEFAULT_LEAGUE_CODE).orElseGet(() -> {
            SimLeagueStateEntity entity = new SimLeagueStateEntity();
            entity.setLeagueCode(DEFAULT_LEAGUE_CODE);
            entity.setAnchorDate(null);
            entity.setCurrentDate(null);
            entity.setRunning(false);
            return simLeagueStateRepository.save(entity);
        });
    }

    private SimAccountEntity getOrCreateAccount(Long userId) {
        return simAccountRepository.findByUserId(userId).orElseGet(() -> {
            SimAccountEntity entity = new SimAccountEntity();
            entity.setUserId(userId);
            entity.setCash(INITIAL_CASH);
            entity.setRealizedPnl(0);
            return simAccountRepository.save(entity);
        });
    }

    private SimReplayStateEntity getOrCreateReplayState(Long userId) {
        return simReplayStateRepository.findByUserId(userId).orElseGet(() -> {
            SimReplayStateEntity entity = new SimReplayStateEntity();
            entity.setUserId(userId);
            entity.setReplayDate(null);
            entity.setAnchorDate(null);
            entity.setRunning(false);
            return simReplayStateRepository.save(entity);
        });
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("유효한 사용자 정보가 필요합니다.");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다.");
        }
        return code.trim();
    }

    private String normalizeSide(String side) {
        if (side == null) {
            throw new IllegalArgumentException("주문 구분(side)이 필요합니다.");
        }
        String normalized = side.trim().toUpperCase();
        if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
            throw new IllegalArgumentException("side는 BUY 또는 SELL만 가능합니다.");
        }
        return normalized;
    }

    private String normalizeOrderType(String orderType) {
        if (orderType == null || orderType.isBlank()) {
            return "MARKET";
        }
        String normalized = orderType.trim().toUpperCase();
        if (!"MARKET".equals(normalized) && !"LIMIT".equals(normalized)) {
            throw new IllegalArgumentException("orderType은 MARKET 또는 LIMIT만 가능합니다.");
        }
        return normalized;
    }

    private Double normalizeLimitPrice(Double limitPrice, String orderType) {
        if (!"LIMIT".equals(orderType)) {
            return null;
        }
        if (limitPrice == null || !Double.isFinite(limitPrice) || limitPrice <= 0) {
            throw new IllegalArgumentException("지정가 주문은 limitPrice가 필요합니다.");
        }
        return round2(limitPrice);
    }

    private boolean isLimitExecutable(String side, double marketPrice, Double limitPrice) {
        if ("BUY".equals(side) && marketPrice > limitPrice) {
            return false;
        }
        if ("SELL".equals(side) && marketPrice < limitPrice) {
            return false;
        }
        return true;
    }

    private void validatePendingOrderCanBePlaced(Long userId, SimAccountEntity account, String code, String side, int qty, Double limitPrice) {
        if ("BUY".equals(side)) {
            double worstCaseAmount = limitPrice * qty;
            if (account.getCash() < worstCaseAmount) {
                throw new IllegalArgumentException("예수금이 부족합니다.");
            }
            return;
        }

        SimPositionEntity position = simPositionRepository.findByUserIdAndCode(userId, code).orElse(null);
        if (position == null || position.getQuantity() < qty) {
            throw new IllegalArgumentException("매도 가능한 수량이 부족합니다.");
        }
    }

    private void executeTrade(Long userId, SimAccountEntity account, String code, String side, int qty, double price) {
        double amount = price * qty;
        if ("BUY".equals(side)) {
            if (account.getCash() < amount) {
                throw new IllegalArgumentException("예수금이 부족합니다.");
            }
            SimPositionEntity position = simPositionRepository.findByUserIdAndCode(userId, code).orElse(null);
            if (position == null) {
                position = new SimPositionEntity();
                position.setUserId(userId);
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
            return;
        }

        SimPositionEntity position = simPositionRepository.findByUserIdAndCode(userId, code).orElse(null);
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

    private void processPendingOrders(Long userId, LocalDate valuationDate) {
        List<SimPendingOrderEntity> orders = simPendingOrderRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (orders.isEmpty()) {
            return;
        }
        SimAccountEntity account = getOrCreateAccount(userId);
        boolean accountChanged = false;
        for (SimPendingOrderEntity order : orders) {
            double marketPrice = stockService.getClosePriceOnOrBefore(order.getCode(), valuationDate);
            if (!isLimitExecutable(order.getSide(), marketPrice, order.getLimitPrice())) {
                continue;
            }
            try {
                executeTrade(userId, account, order.getCode(), order.getSide(), order.getQuantity(), marketPrice);
                recordExecution(userId, order.getCode(), order.getSide(), order.getOrderType(), order.getLimitPrice(), order.getQuantity(), marketPrice, valuationDate);
                accountChanged = true;
                simPendingOrderRepository.delete(order);
            } catch (IllegalArgumentException e) {
                simPendingOrderRepository.delete(order);
            }
        }
        if (accountChanged) {
            simAccountRepository.save(account);
        }
    }

    private LocalDate parseStartDateOrDefault(String startDate) {
        if (startDate == null || startDate.isBlank()) {
            return DEFAULT_REPLAY_START_DATE;
        }
        try {
            return LocalDate.parse(startDate.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("startDate must be yyyy-MM-dd");
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void recordExecution(Long userId, String code, String side, String orderType, Double requestedLimitPrice, int qty, double price, LocalDate valuationDate) {
        SimTradeExecutionEntity row = new SimTradeExecutionEntity();
        row.setUserId(userId);
        row.setCode(code);
        row.setSide(side);
        row.setOrderType(orderType);
        row.setRequestedLimitPrice(requestedLimitPrice);
        row.setQuantity(qty);
        row.setPrice(round2(price));
        row.setAmount(round2(price * qty));
        row.setValuationDate(valuationDate == null ? null : valuationDate.toString());
        row.setExecutedAt(System.currentTimeMillis());
        simTradeExecutionRepository.save(row);
    }

    private record SimRankingRow(
            Long userId,
            String userName,
            double totalValue,
            double returnRate,
            double realizedPnl,
            double unrealizedPnl,
            String valuationDate
    ) {}
}
