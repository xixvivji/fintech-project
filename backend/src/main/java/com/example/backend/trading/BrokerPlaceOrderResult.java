package com.example.backend.trading;

public class BrokerPlaceOrderResult {
    private String brokerOrderId;
    private String status;
    private String message;

    public BrokerPlaceOrderResult(String brokerOrderId, String status, String message) {
        this.brokerOrderId = brokerOrderId;
        this.status = status;
        this.message = message;
    }

    public String getBrokerOrderId() {
        return brokerOrderId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
