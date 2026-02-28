package com.example.backend.simulation;

import com.example.backend.auth.UserEntity;
import com.example.backend.auth.UserRepository;
import com.example.backend.simulation.time.TimeEngine;
import com.example.backend.simulation.time.TimeEngineProvider;
import com.example.backend.stock.StockService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Service
public class SimulationService {
    private static final double INITIAL_CASH = 50_000_000;
    private static final LocalDate DEFAULT_REPLAY_START_DATE = LocalDate.of(2020, 1, 1);
    private static final String DEFAULT_LEAGUE_CODE = "MAIN";
    private static final String LEAGUE_ADMIN_NAME = "admin";
    private static final String ORDER_STATUS_RECEIVED = "RECEIVED";
    private static final String ORDER_STATUS_PROCESSED = "PROCESSED";
    private static final String ORDER_STATUS_FAILED = "FAILED";

    private final StockService stockService;
    private final SimAccountRepository simAccountRepository;
    private final SimPositionRepository simPositionRepository;
    private final SimReplayStateRepository simReplayStateRepository;
    private final SimPendingOrderRepository simPendingOrderRepository;
    private final SimTradeExecutionRepository simTradeExecutionRepository;
    private final SimAutoBuyRuleRepository simAutoBuyRuleRepository;
    private final SimLeagueStateRepository simLeagueStateRepository;
    private final SimOrderEventRepository simOrderEventRepository;
    private final TimeEngineProvider timeEngineProvider;
    private final SimOrderQueueProperties orderQueueProperties;
    private final UserRepository userRepository;
    private final ScheduledExecutorService replayExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService orderQueueExecutor = Executors.newSingleThreadScheduledExecutor();

    public SimulationService(
            StockService stockService,
            SimAccountRepository simAccountRepository,
            SimPositionRepository simPositionRepository,
            SimReplayStateRepository simReplayStateRepository,
            SimPendingOrderRepository simPendingOrderRepository,
            SimTradeExecutionRepository simTradeExecutionRepository,
            SimAutoBuyRuleRepository simAutoBuyRuleRepository,
            SimLeagueStateRepository simLeagueStateRepository,
            SimOrderEventRepository simOrderEventRepository,
            TimeEngineProvider timeEngineProvider,
            SimOrderQueueProperties orderQueueProperties,
            UserRepository userRepository
    ) {
        this.stockService = stockService;
        this.simAccountRepository = simAccountRepository;
        this.simPositionRepository = simPositionRepository;
        this.simReplayStateRepository = simReplayStateRepository;
        this.simPendingOrderRepository = simPendingOrderRepository;
        this.simTradeExecutionRepository = simTradeExecutionRepository;
        this.simAutoBuyRuleRepository = simAutoBuyRuleRepository;
        this.simLeagueStateRepository = simLeagueStateRepository;
        this.simOrderEventRepository = simOrderEventRepository;
        this.timeEngineProvider = timeEngineProvider;
        this.orderQueueProperties = orderQueueProperties;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void startReplayTicker() {
        TimeEngine engine = timeEngineProvider.get();
        int tickSeconds = engine.tickSeconds();
        replayExecutor.scheduleAtFixedRate(this::advanceReplayIfRunning, tickSeconds, tickSeconds, TimeUnit.SECONDS);

        int pollMillis = Math.max(200, orderQueueProperties.getPollMillis());
        orderQueueExecutor.scheduleAtFixedRate(this::processOrderQueueIfEnabled, pollMillis, pollMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdownReplayTicker() {
        replayExecutor.shutdownNow();
        orderQueueExecutor.shutdownNow();
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

        LocalDate valuationDate = resolveValuationDate(userId);
        if (orderQueueProperties.isAsyncEnabled()) {
            return enqueueOrder(userId, code, side, orderType, qty, limitPrice, valuationDate);
        }

        return executeOrderSynchronously(userId, code, side, orderType, qty, limitPrice, valuationDate);
    }

    private SimOrderResponseDto executeOrderSynchronously(
            Long userId,
            String code,
            String side,
            String orderType,
            int qty,
            Double limitPrice,
            LocalDate valuationDate
    ) {
        SimAccountEntity account = getOrCreateAccount(userId);

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

    private SimOrderResponseDto enqueueOrder(
            Long userId,
            String code,
            String side,
            String orderType,
            int qty,
            Double limitPrice,
            LocalDate valuationDate
    ) {
        String idempotencyKey = UUID.randomUUID().toString();
        SimOrderEventEntity event = new SimOrderEventEntity();
        event.setIdempotencyKey(idempotencyKey);
        event.setUserId(userId);
        event.setCode(code);
        event.setSide(side);
        event.setOrderType(orderType);
        event.setLimitPrice(limitPrice);
        event.setQuantity(qty);
        event.setValuationDate(valuationDate.toString());
        event.setStatus(ORDER_STATUS_RECEIVED);
        event.setRetryCount(0);
        event.setCreatedAt(System.currentTimeMillis());
        simOrderEventRepository.save(event);

        return new SimOrderResponseDto(
                "ACCEPTED",
                "주문이 접수되었습니다. 비동기 체결 대기열에서 처리됩니다.",
                code,
                side,
                orderType,
                limitPrice,
                qty,
                null,
                null,
                null,
                event.getCreatedAt()
        );
    }

    @Transactional
    public synchronized PortfolioResponseDto startReplay(Long userId, String startDate) {
        validateUserId(userId);
        validateAdminUser(userId);
        TimeEngine engine = timeEngineProvider.get();

        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        if (leagueState.getAnchorDate() == null || leagueState.getAnchorDate().isBlank()) {
            LocalDate date = engine.parseAnchorDate(startDate, DEFAULT_REPLAY_START_DATE);
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
        validateAdminUser(userId);

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
        TimeEngine engine = timeEngineProvider.get();
        return new ReplayStateDto(
                portfolio.getValuationDate(),
                leagueState.getAnchorDate(),
                leagueState.isRunning(),
                engine.stepDays(),
                engine.tickSeconds(),
                portfolio
        );
    }

    @Transactional
    public synchronized SimLeagueStateDto getLeagueState(Long userId) {
        validateUserId(userId);
        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        TimeEngine engine = timeEngineProvider.get();
        return new SimLeagueStateDto(
                DEFAULT_LEAGUE_CODE,
                leagueState.getAnchorDate(),
                leagueState.getCurrentDate(),
                leagueState.isRunning(),
                engine.stepDays(),
                engine.tickSeconds()
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
    public synchronized List<SimAutoBuyRuleDto> getAutoBuyRules(Long userId) {
        validateUserId(userId);
        return simAutoBuyRuleRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toAutoBuyRuleDto)
                .toList();
    }

    @Transactional
    public synchronized SimAutoBuyRuleDto createAutoBuyRule(Long userId, SimAutoBuyRuleRequestDto request) {
        validateUserId(userId);
        if (request == null) throw new IllegalArgumentException("Auto buy rule request is empty.");
        SimAutoBuyRuleEntity row = new SimAutoBuyRuleEntity();
        row.setUserId(userId);
        row.setName(normalizeAutoRuleName(request.getName()));
        row.setCode(normalizeCode(request.getCode()));
        int qty = request.getQuantity() == null ? 1 : request.getQuantity();
        if (qty <= 0) throw new IllegalArgumentException("quantity must be at least 1.");
        row.setQuantity(qty);
        row.setFrequency(normalizeAutoRuleFrequency(request.getFrequency()));
        row.setEnabled(request.getEnabled() == null || request.getEnabled());
        row.setStartDate(normalizeOptionalDate(request.getStartDate()));
        row.setEndDate(normalizeOptionalDate(request.getEndDate()));
        row.setLastExecutedDate(null);
        row.setLastExecutedAt(0L);
        row.setCreatedAt(System.currentTimeMillis());
        return toAutoBuyRuleDto(simAutoBuyRuleRepository.save(row));
    }

    @Transactional
    public synchronized SimAutoBuyRuleDto updateAutoBuyRule(Long userId, Long ruleId, SimAutoBuyRuleRequestDto request) {
        validateUserId(userId);
        if (request == null) throw new IllegalArgumentException("Auto buy rule request is empty.");
        SimAutoBuyRuleEntity row = simAutoBuyRuleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Auto buy rule not found."));
        if (request.getName() != null) row.setName(normalizeAutoRuleName(request.getName()));
        if (request.getCode() != null) row.setCode(normalizeCode(request.getCode()));
        if (request.getQuantity() != null) {
            if (request.getQuantity() <= 0) throw new IllegalArgumentException("quantity must be at least 1.");
            row.setQuantity(request.getQuantity());
        }
        if (request.getFrequency() != null) row.setFrequency(normalizeAutoRuleFrequency(request.getFrequency()));
        if (request.getEnabled() != null) row.setEnabled(request.getEnabled());
        if (request.getStartDate() != null) row.setStartDate(normalizeOptionalDate(request.getStartDate()));
        if (request.getEndDate() != null) row.setEndDate(normalizeOptionalDate(request.getEndDate()));
        return toAutoBuyRuleDto(simAutoBuyRuleRepository.save(row));
    }

    @Transactional
    public synchronized void deleteAutoBuyRule(Long userId, Long ruleId) {
        validateUserId(userId);
        SimAutoBuyRuleEntity row = simAutoBuyRuleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Auto buy rule not found."));
        simAutoBuyRuleRepository.delete(row);
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

        rows.sort((a, b) -> {
            int byReturnRate = Double.compare(b.returnRate(), a.returnRate());
            if (byReturnRate != 0) return byReturnRate;
            int byTotalValue = Double.compare(b.totalValue(), a.totalValue());
            if (byTotalValue != 0) return byTotalValue;
            return Long.compare(a.userId(), b.userId());
        });

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
    public synchronized List<SimPopularStockDto> getPopularStocks(Long currentUserId, int limit, int days) {
        validateUserId(currentUserId);
        int safeLimit = Math.max(1, Math.min(limit, 20));
        int safeDays = Math.max(1, Math.min(days, 30));

        LocalDate leagueDate = LocalDate.parse(getOrCreateLeagueState().getCurrentDate());
        LocalDate startDate = leagueDate.minusDays(safeDays - 1L);

        List<SimTradeExecutionEntity> executions = simTradeExecutionRepository.findByValuationDateBetween(
                startDate.toString(),
                leagueDate.toString()
        );
        if (executions.isEmpty()) {
            executions = simTradeExecutionRepository.findByValuationDateLessThanEqual(leagueDate.toString());
        }

        Map<String, PopularStockAggregate> aggregateMap = new HashMap<>();
        for (SimTradeExecutionEntity execution : executions) {
            PopularStockAggregate agg = aggregateMap.computeIfAbsent(execution.getCode(), code -> new PopularStockAggregate());
            agg.code = execution.getCode();
            agg.executionCount++;
            agg.totalQuantity += execution.getQuantity();
            if (agg.latestExecutedAt < execution.getExecutedAt()) {
                agg.latestExecutedAt = execution.getExecutedAt();
                agg.latestValuationDate = execution.getValuationDate();
            }
        }

        return aggregateMap.values().stream()
                .sorted((a, b) -> {
                    int byCount = Long.compare(b.executionCount, a.executionCount);
                    if (byCount != 0) return byCount;
                    int byQty = Long.compare(b.totalQuantity, a.totalQuantity);
                    if (byQty != 0) return byQty;
                    return a.code.compareTo(b.code);
                })
                .limit(safeLimit)
                .map(a -> new SimPopularStockDto(
                        a.code,
                        a.latestValuationDate == null ? leagueDate.toString() : a.latestValuationDate,
                        a.executionCount,
                        a.totalQuantity,
                        round2(stockService.getClosePriceOnOrBefore(a.code, leagueDate))
                ))
                .toList();
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
        validateAdminUser(userId);

        List<UserEntity> users = userRepository.findAll();
        for (UserEntity user : users) {
            Long targetUserId = user.getId();
            if (targetUserId == null) continue;

            SimAccountEntity account = getOrCreateAccount(targetUserId);
            account.setCash(INITIAL_CASH);
            account.setRealizedPnl(0);
            simAccountRepository.save(account);

            simPositionRepository.deleteByUserId(targetUserId);
            simPendingOrderRepository.deleteByUserId(targetUserId);
            simTradeExecutionRepository.deleteByUserId(targetUserId);

            SimReplayStateEntity replayState = getOrCreateReplayState(targetUserId);
            replayState.setReplayDate(null);
            replayState.setAnchorDate(null);
            replayState.setRunning(false);
            simReplayStateRepository.save(replayState);
        }

        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        leagueState.setAnchorDate(null);
        leagueState.setCurrentDate(null);
        leagueState.setRunning(false);
        simLeagueStateRepository.save(leagueState);
    }

    @Transactional
    protected synchronized void advanceReplayIfRunning() {
        TimeEngine engine = timeEngineProvider.get();
        SimLeagueStateEntity leagueState = getOrCreateLeagueState();
        if (!leagueState.isRunning() || leagueState.getCurrentDate() == null || leagueState.getCurrentDate().isBlank()) {
            return;
        }

        LocalDate next = engine.nextDate(LocalDate.parse(leagueState.getCurrentDate()));
        leagueState.setCurrentDate(next.toString());
        simLeagueStateRepository.save(leagueState);

        syncUserReplayDates(next);
        processAutoBuyRulesForAllUsers(next);
        processPendingOrdersForAllUsers(next);
    }

    @Transactional
    protected synchronized void processOrderQueueIfEnabled() {
        if (!orderQueueProperties.isAsyncEnabled()) {
            return;
        }
        int batchSize = Math.max(1, Math.min(orderQueueProperties.getBatchSize(), 1000));
        List<SimOrderEventEntity> events = simOrderEventRepository.findByStatusOrderByIdAsc(
                ORDER_STATUS_RECEIVED,
                PageRequest.of(0, batchSize)
        );
        for (SimOrderEventEntity event : events) {
            processOrderEvent(event);
        }
    }

    private void processOrderEvent(SimOrderEventEntity event) {
        try {
            LocalDate valuationDate = event.getValuationDate() == null || event.getValuationDate().isBlank()
                    ? resolveValuationDate(event.getUserId())
                    : LocalDate.parse(event.getValuationDate());
            executeOrderSynchronously(
                    event.getUserId(),
                    event.getCode(),
                    event.getSide(),
                    event.getOrderType(),
                    event.getQuantity(),
                    event.getLimitPrice(),
                    valuationDate
            );
            event.setStatus(ORDER_STATUS_PROCESSED);
            event.setProcessedAt(System.currentTimeMillis());
            event.setErrorMessage(null);
        } catch (Exception e) {
            int nextRetry = event.getRetryCount() + 1;
            event.setRetryCount(nextRetry);
            event.setErrorMessage(trimError(e.getMessage()));
            if (nextRetry >= Math.max(1, orderQueueProperties.getMaxRetries())) {
                event.setStatus(ORDER_STATUS_FAILED);
                event.setProcessedAt(System.currentTimeMillis());
            }
        }
        simOrderEventRepository.save(event);
    }

    private String trimError(String message) {
        if (message == null) return null;
        String clean = message.trim();
        if (clean.length() <= 500) return clean;
        return clean.substring(0, 500);
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
        return timeEngineProvider.get().defaultAnchorDate();
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

    private void processAutoBuyRulesForAllUsers(LocalDate valuationDate) {
        List<SimAutoBuyRuleEntity> rules = simAutoBuyRuleRepository.findByEnabledTrue();
        if (rules.isEmpty()) return;
        for (SimAutoBuyRuleEntity rule : rules) {
            try {
                processAutoBuyRule(rule, valuationDate);
            } catch (Exception ignored) {
                // Draft scheduler: skip failures and continue processing other rules.
            }
        }
    }

    private void processAutoBuyRule(SimAutoBuyRuleEntity rule, LocalDate valuationDate) {
        if (rule == null || rule.getUserId() == null || valuationDate == null) return;
        String vd = valuationDate.toString();
        if (vd.equals(rule.getLastExecutedDate())) return;
        if (rule.getStartDate() != null && !rule.getStartDate().isBlank() && valuationDate.isBefore(LocalDate.parse(rule.getStartDate()))) return;
        if (rule.getEndDate() != null && !rule.getEndDate().isBlank() && valuationDate.isAfter(LocalDate.parse(rule.getEndDate()))) return;
        if (!autoRuleRunsOn(rule.getFrequency(), valuationDate)) return;

        SimAccountEntity account = getOrCreateAccount(rule.getUserId());
        double price;
        try {
            price = stockService.getClosePriceOnOrBefore(rule.getCode(), valuationDate);
        } catch (Exception e) {
            return;
        }
        try {
            executeTrade(rule.getUserId(), account, rule.getCode(), "BUY", rule.getQuantity(), price);
            simAccountRepository.save(account);
            recordExecution(rule.getUserId(), rule.getCode(), "BUY", "MARKET", null, rule.getQuantity(), price, valuationDate);
            rule.setLastExecutedDate(vd);
            rule.setLastExecutedAt(System.currentTimeMillis());
            simAutoBuyRuleRepository.save(rule);
        } catch (IllegalArgumentException ignored) {
            // Insufficient cash or invalid condition: skip this date.
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

    private void validateAdminUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        String name = user.getName() == null ? "" : user.getName().trim();
        if (!LEAGUE_ADMIN_NAME.equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("Only admin can control the league.");
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

    private String normalizeAutoRuleName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Rule name is required.");
        String n = name.trim();
        if (n.length() > 100) throw new IllegalArgumentException("Rule name is too long.");
        return n;
    }

    private String normalizeAutoRuleFrequency(String frequency) {
        String f = frequency == null ? "DAILY" : frequency.trim().toUpperCase();
        if (!"DAILY".equals(f) && !"WEEKDAY".equals(f)) {
            throw new IllegalArgumentException("frequency must be DAILY or WEEKDAY.");
        }
        return f;
    }

    private boolean autoRuleRunsOn(String frequency, LocalDate valuationDate) {
        String f = normalizeAutoRuleFrequency(frequency);
        if ("DAILY".equals(f)) return true;
        return valuationDate.getDayOfWeek().getValue() <= 5;
    }

    private String normalizeOptionalDate(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isBlank()) return null;
        try {
            return LocalDate.parse(v).toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Date must be yyyy-MM-dd.");
        }
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

    private SimAutoBuyRuleDto toAutoBuyRuleDto(SimAutoBuyRuleEntity row) {
        return new SimAutoBuyRuleDto(
                row.getId(),
                row.getName(),
                row.getCode(),
                row.getQuantity(),
                row.getFrequency(),
                row.isEnabled(),
                row.getStartDate(),
                row.getEndDate(),
                row.getLastExecutedDate(),
                row.getLastExecutedAt(),
                row.getCreatedAt()
        );
    }

    private static class PopularStockAggregate {
        private String code;
        private long executionCount;
        private long totalQuantity;
        private long latestExecutedAt;
        private String latestValuationDate;
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
