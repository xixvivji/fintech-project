package com.example.backend.trading;

import com.example.backend.auth.UserRepository;
import com.example.backend.stock.StockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TradingService {
    private final UserRepository userRepository;
    private final StockService stockService;
    private final TradingBrokerProperties brokerProperties;
    private final BrokerClient brokerClient;
    private final BrokerAccountLinkRepository brokerAccountLinkRepository;
    private final BrokerOrderRepository brokerOrderRepository;
    private final BrokerExecutionRepository brokerExecutionRepository;
    private final PositionSnapshotRepository positionSnapshotRepository;

    public TradingService(
            UserRepository userRepository,
            StockService stockService,
            TradingBrokerProperties brokerProperties,
            BrokerClient brokerClient,
            BrokerAccountLinkRepository brokerAccountLinkRepository,
            BrokerOrderRepository brokerOrderRepository,
            BrokerExecutionRepository brokerExecutionRepository,
            PositionSnapshotRepository positionSnapshotRepository
    ) {
        this.userRepository = userRepository;
        this.stockService = stockService;
        this.brokerProperties = brokerProperties;
        this.brokerClient = brokerClient;
        this.brokerAccountLinkRepository = brokerAccountLinkRepository;
        this.brokerOrderRepository = brokerOrderRepository;
        this.brokerExecutionRepository = brokerExecutionRepository;
        this.positionSnapshotRepository = positionSnapshotRepository;
    }

    @Transactional
    public TradingAccountLinkDto upsertAccountLink(Long userId, TradingAccountLinkRequestDto request) {
        validateUser(userId);
        if (request == null) throw new IllegalArgumentException("Link request is empty.");
        String provider = normalizeProvider(request.getProvider());
        String accountNo = normalizeAccountNo(request.getAccountNo());
        String token = normalizeRequiredText(request.getAccessToken(), "accessToken");

        BrokerAccountLinkEntity row = brokerAccountLinkRepository.findByUserIdAndProvider(userId, provider)
                .orElseGet(BrokerAccountLinkEntity::new);
        long now = System.currentTimeMillis();

        if (row.getId() == null) {
            row.setUserId(userId);
            row.setProvider(provider);
            row.setCreatedAt(now);
        }

        row.setAccountNo(accountNo);
        // Placeholder: use KMS/strong encryption in production.
        row.setAccessTokenEncrypted(token);
        row.setEnabled(true);
        row.setUpdatedAt(now);

        BrokerAccountLinkEntity saved = brokerAccountLinkRepository.save(row);
        return toLinkDto(saved);
    }

    @Transactional(readOnly = true)
    public TradingAccountLinkDto getMyAccountLink(Long userId) {
        validateUser(userId);
        BrokerAccountLinkEntity row = getRequiredAccountLink(userId);
        return toLinkDto(row);
    }

    @Transactional(readOnly = true)
    public TradingPortfolioDto getPortfolio(Long userId) {
        validateUser(userId);
        BrokerAccountLinkEntity link = getRequiredAccountLink(userId);

        BrokerAccountSnapshotDto snapshot = brokerClient.getAccountSnapshot(link);
        List<BrokerPositionDto> positions = snapshot.getPositions();

        if (brokerProperties.isMockEnabled()) {
            positions = buildPositionsFromSnapshots(userId);
            double marketValue = positions.stream().mapToDouble(p -> p.getLastPrice() * p.getQuantity()).sum();
            snapshot = new BrokerAccountSnapshotDto(0, round2(marketValue), positions);
        }

        return new TradingPortfolioDto(
                link.getProvider(),
                maskAccountNo(link.getAccountNo()),
                round2(snapshot.getCash()),
                round2(snapshot.getTotalAsset()),
                positions,
                System.currentTimeMillis()
        );
    }

    @Transactional
    public TradingOrderResponseDto placeOrder(Long userId, TradingOrderRequestDto request) {
        validateUser(userId);
        BrokerAccountLinkEntity link = getRequiredAccountLink(userId);
        if (request == null) throw new IllegalArgumentException("Order request is empty.");

        String code = normalizeCode(request.getCode());
        String side = normalizeSide(request.getSide());
        String orderType = normalizeOrderType(request.getOrderType());
        int quantity = request.getQuantity() == null ? 0 : request.getQuantity();
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be at least 1.");
        Double limitPrice = normalizeLimitPrice(orderType, request.getLimitPrice());

        long now = System.currentTimeMillis();
        String clientOrderId = UUID.randomUUID().toString();

        BrokerOrderEntity order = new BrokerOrderEntity();
        order.setUserId(userId);
        order.setProvider(link.getProvider());
        order.setAccountNo(link.getAccountNo());
        order.setClientOrderId(clientOrderId);
        order.setStatus("CREATED");
        order.setCode(code);
        order.setSide(side);
        order.setOrderType(orderType);
        order.setLimitPrice(limitPrice);
        order.setQuantity(quantity);
        order.setFilledQuantity(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = brokerOrderRepository.save(order);

        BrokerPlaceOrderResult brokerResult = brokerClient.placeOrder(link, request, clientOrderId);
        order.setBrokerOrderId(brokerResult.getBrokerOrderId());
        order.setStatus(brokerResult.getStatus());
        order.setErrorMessage(brokerResult.getMessage());
        order.setUpdatedAt(System.currentTimeMillis());

        if (brokerProperties.isMockEnabled()) {
            fillOrderInMock(order);
        } else {
            brokerOrderRepository.save(order);
        }

        return new TradingOrderResponseDto(
                order.getId(),
                order.getClientOrderId(),
                order.getBrokerOrderId(),
                order.getStatus(),
                order.getErrorMessage(),
                order.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<TradingOrderDto> getOrders(Long userId) {
        validateUser(userId);
        return brokerOrderRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toOrderDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TradingExecutionDto> getExecutions(Long userId) {
        validateUser(userId);
        return brokerExecutionRepository.findTop100ByUserIdOrderByExecutedAtDesc(userId).stream()
                .map(row -> new TradingExecutionDto(
                        row.getId(),
                        row.getOrderId(),
                        row.getBrokerExecutionId(),
                        row.getCode(),
                        row.getSide(),
                        row.getQuantity(),
                        round2(row.getPrice()),
                        row.getExecutedAt()
                ))
                .toList();
    }

    private void fillOrderInMock(BrokerOrderEntity order) {
        double price = stockService.getClosePriceOnOrBefore(order.getCode(), LocalDate.now());
        int qty = order.getQuantity();

        order.setStatus("FILLED");
        order.setFilledQuantity(qty);
        order.setAvgFilledPrice(round2(price));
        order.setUpdatedAt(System.currentTimeMillis());
        brokerOrderRepository.save(order);

        BrokerExecutionEntity execution = new BrokerExecutionEntity();
        execution.setOrderId(order.getId());
        execution.setUserId(order.getUserId());
        execution.setBrokerExecutionId("MOCK-EXE-" + UUID.randomUUID());
        execution.setCode(order.getCode());
        execution.setSide(order.getSide());
        execution.setQuantity(qty);
        execution.setPrice(round2(price));
        execution.setExecutedAt(System.currentTimeMillis());
        brokerExecutionRepository.save(execution);

        upsertPositionSnapshot(order.getUserId(), order.getCode(), order.getSide(), qty, price);
    }

    private void upsertPositionSnapshot(Long userId, String code, String side, int qty, double price) {
        Map<String, PositionSnapshotEntity> latest = latestSnapshotByCode(userId);
        PositionSnapshotEntity prev = latest.get(code);

        int prevQty = prev == null ? 0 : prev.getQuantity();
        double prevAvg = prev == null ? 0 : prev.getAvgPrice();
        int nextQty;
        double nextAvg;

        if ("BUY".equals(side)) {
            nextQty = prevQty + qty;
            nextAvg = nextQty == 0 ? 0 : ((prevQty * prevAvg) + (qty * price)) / nextQty;
        } else {
            nextQty = Math.max(0, prevQty - qty);
            nextAvg = prevAvg;
        }

        PositionSnapshotEntity row = new PositionSnapshotEntity();
        row.setUserId(userId);
        row.setCode(code);
        row.setQuantity(nextQty);
        row.setAvgPrice(round2(nextAvg));
        row.setLastPrice(round2(price));
        row.setMarketValue(round2(nextQty * price));
        row.setSnapshotAt(System.currentTimeMillis());
        positionSnapshotRepository.save(row);
    }

    private List<BrokerPositionDto> buildPositionsFromSnapshots(Long userId) {
        Map<String, PositionSnapshotEntity> latest = latestSnapshotByCode(userId);
        List<BrokerPositionDto> result = new ArrayList<>();
        for (PositionSnapshotEntity row : latest.values()) {
            if (row.getQuantity() <= 0) continue;
            result.add(new BrokerPositionDto(
                    row.getCode(),
                    row.getQuantity(),
                    round2(row.getAvgPrice()),
                    round2(row.getLastPrice())
            ));
        }
        return result;
    }

    private Map<String, PositionSnapshotEntity> latestSnapshotByCode(Long userId) {
        List<PositionSnapshotEntity> rows = positionSnapshotRepository.findTop100ByUserIdOrderBySnapshotAtDesc(userId);
        Map<String, PositionSnapshotEntity> latest = new LinkedHashMap<>();
        for (PositionSnapshotEntity row : rows) {
            if (!latest.containsKey(row.getCode())) {
                latest.put(row.getCode(), row);
            }
        }
        return latest;
    }

    private BrokerAccountLinkEntity getRequiredAccountLink(Long userId) {
        return brokerAccountLinkRepository.findByUserIdAndEnabledTrue(userId)
                .orElseThrow(() -> new IllegalArgumentException("Linked trading account not found."));
    }

    private TradingAccountLinkDto toLinkDto(BrokerAccountLinkEntity row) {
        return new TradingAccountLinkDto(
                row.getUserId(),
                row.getProvider(),
                maskAccountNo(row.getAccountNo()),
                row.isEnabled(),
                row.getUpdatedAt()
        );
    }

    private TradingOrderDto toOrderDto(BrokerOrderEntity row) {
        return new TradingOrderDto(
                row.getId(),
                row.getClientOrderId(),
                row.getBrokerOrderId(),
                row.getCode(),
                row.getSide(),
                row.getOrderType(),
                row.getLimitPrice(),
                row.getQuantity(),
                row.getFilledQuantity(),
                row.getAvgFilledPrice() == null ? null : round2(row.getAvgFilledPrice()),
                row.getStatus(),
                row.getErrorMessage(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private void validateUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid user id.");
        }
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found.");
        }
    }

    private String normalizeProvider(String provider) {
        String fallback = brokerProperties.getProvider();
        String value = provider == null || provider.isBlank() ? fallback : provider;
        return normalizeRequiredText(value, "provider").toUpperCase();
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private String normalizeAccountNo(String accountNo) {
        String value = normalizeRequiredText(accountNo, "accountNo");
        if (value.length() < 8) {
            throw new IllegalArgumentException("accountNo is too short.");
        }
        return value;
    }

    private String normalizeCode(String code) {
        String value = normalizeRequiredText(code, "code");
        if (!value.matches("\\d{6}")) {
            throw new IllegalArgumentException("code must be 6 digits.");
        }
        return value;
    }

    private String normalizeSide(String side) {
        String value = normalizeRequiredText(side, "side").toUpperCase();
        if (!"BUY".equals(value) && !"SELL".equals(value)) {
            throw new IllegalArgumentException("side must be BUY or SELL.");
        }
        return value;
    }

    private String normalizeOrderType(String orderType) {
        if (orderType == null || orderType.isBlank()) return "MARKET";
        String value = orderType.trim().toUpperCase();
        if (!"MARKET".equals(value) && !"LIMIT".equals(value)) {
            throw new IllegalArgumentException("orderType must be MARKET or LIMIT.");
        }
        return value;
    }

    private Double normalizeLimitPrice(String orderType, Double limitPrice) {
        if (!"LIMIT".equals(orderType)) return null;
        if (limitPrice == null || !Double.isFinite(limitPrice) || limitPrice <= 0) {
            throw new IllegalArgumentException("limitPrice is required for LIMIT order.");
        }
        return round2(limitPrice);
    }

    private String maskAccountNo(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) return "";
        if (accountNo.length() <= 4) return "****";
        return "****" + accountNo.substring(accountNo.length() - 4);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
