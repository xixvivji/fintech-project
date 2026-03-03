package com.example.backend.trading;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class SsafyBrokerClient implements BrokerClient {
    private final TradingBrokerProperties properties;

    public SsafyBrokerClient(TradingBrokerProperties properties) {
        this.properties = properties;
    }

    @Override
    public BrokerAccountSnapshotDto getAccountSnapshot(BrokerAccountLinkEntity accountLink) {
        if (properties.isMockEnabled()) {
            List<BrokerPositionDto> empty = new ArrayList<>();
            return new BrokerAccountSnapshotDto(0, 0, empty);
        }
        throw new UnsupportedOperationException("SSAFY broker API integration is not implemented yet.");
    }

    @Override
    public BrokerPlaceOrderResult placeOrder(BrokerAccountLinkEntity accountLink, TradingOrderRequestDto request, String clientOrderId) {
        if (properties.isMockEnabled()) {
            String brokerOrderId = "MOCK-" + UUID.randomUUID();
            return new BrokerPlaceOrderResult(brokerOrderId, "ACCEPTED", "Mock broker accepted the order.");
        }
        throw new UnsupportedOperationException("SSAFY broker API integration is not implemented yet.");
    }
}
