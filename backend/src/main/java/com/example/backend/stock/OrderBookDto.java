package com.example.backend.stock;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "??? ??/???? ??")
public class OrderBookDto {
    @Schema(description = "????", example = "005930")
    private String code;

    @Schema(description = "????(HHmmss)", example = "144057")
    private String time;

    @Schema(description = "???", example = "186600.0", nullable = true)
    private Double currentPrice;

    @Schema(description = "? ????", example = "76447", nullable = true)
    private Long totalAskQty;

    @Schema(description = "? ????", example = "90500", nullable = true)
    private Long totalBidQty;

    @Schema(description = "????", example = "118.38", nullable = true)
    private Double executionStrength;

    @Schema(description = "?? ?? ??")
    private List<OrderBookLevelDto> levels;

    public OrderBookDto(String code, String time, Double currentPrice, Long totalAskQty, Long totalBidQty, Double executionStrength, List<OrderBookLevelDto> levels) {
        this.code = code;
        this.time = time;
        this.currentPrice = currentPrice;
        this.totalAskQty = totalAskQty;
        this.totalBidQty = totalBidQty;
        this.executionStrength = executionStrength;
        this.levels = levels;
    }

    public String getCode() { return code; }
    public String getTime() { return time; }
    public Double getCurrentPrice() { return currentPrice; }
    public Long getTotalAskQty() { return totalAskQty; }
    public Long getTotalBidQty() { return totalBidQty; }
    public Double getExecutionStrength() { return executionStrength; }
    public List<OrderBookLevelDto> getLevels() { return levels; }
}
