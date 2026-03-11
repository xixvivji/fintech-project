package com.example.backend.simulation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "???? ?? ??")
public class SimOrderRequestDto {
    @Schema(description = "6?? ????", example = "005930")
    private String code;

    @Schema(description = "?? ??", example = "BUY", allowableValues = {"BUY", "SELL"})
    private String side;

    @Schema(description = "?? ??", example = "LIMIT", allowableValues = {"MARKET", "LIMIT"})
    private String orderType;

    @Schema(description = "??? ?? ??(MARKET?? null)", example = "186500", nullable = true)
    private Double limitPrice;

    @Schema(description = "?? ??", example = "3")
    private int quantity;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public Double getLimitPrice() { return limitPrice; }
    public void setLimitPrice(Double limitPrice) { this.limitPrice = limitPrice; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
