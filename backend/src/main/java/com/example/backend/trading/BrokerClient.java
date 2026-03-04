package com.example.backend.trading;

public interface BrokerClient {
    BrokerAccountSnapshotDto getAccountSnapshot(BrokerAccountLinkEntity accountLink);

    BrokerPlaceOrderResult placeOrder(BrokerAccountLinkEntity accountLink, TradingOrderRequestDto request, String clientOrderId);

    BrokerOrderStatusResult getOrderStatus(BrokerAccountLinkEntity accountLink, BrokerOrderEntity order);
}
