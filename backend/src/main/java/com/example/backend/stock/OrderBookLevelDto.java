package com.example.backend.stock;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "?? ??")
public class OrderBookLevelDto {
    @Schema(description = "?? ??(1? ???)", example = "1")
    private int level;

    @Schema(description = "?? ??", example = "186600.0", nullable = true)
    private Double askPrice;

    @Schema(description = "?? ??", example = "7578", nullable = true)
    private Long askQty;

    @Schema(description = "?? ??", example = "186500.0", nullable = true)
    private Double bidPrice;

    @Schema(description = "?? ??", example = "7666", nullable = true)
    private Long bidQty;

    public OrderBookLevelDto(int level, Double askPrice, Long askQty, Double bidPrice, Long bidQty) {
        this.level = level;
        this.askPrice = askPrice;
        this.askQty = askQty;
        this.bidPrice = bidPrice;
        this.bidQty = bidQty;
    }

    public int getLevel() { return level; }
    public Double getAskPrice() { return askPrice; }
    public Long getAskQty() { return askQty; }
    public Double getBidPrice() { return bidPrice; }
    public Long getBidQty() { return bidQty; }
}
