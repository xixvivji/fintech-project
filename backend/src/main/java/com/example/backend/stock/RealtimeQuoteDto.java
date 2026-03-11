package com.example.backend.stock;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "??? ?? ??")
public class RealtimeQuoteDto {
    @Schema(description = "????", example = "005930")
    private String code;

    @Schema(description = "????(HHmmss)", example = "144057")
    private String time;

    @Schema(description = "???", example = "186600.0")
    private double price;

    @Schema(description = "??", example = "187600.0", nullable = true)
    private Double open;

    @Schema(description = "??", example = "191500.0", nullable = true)
    private Double high;

    @Schema(description = "??", example = "184300.0", nullable = true)
    private Double low;

    @Schema(description = "????", example = "13100.0", nullable = true)
    private Double change;

    @Schema(description = "???(%)", example = "7.55", nullable = true)
    private Double changeRate;

    @Schema(description = "?? ???", example = "27688959", nullable = true)
    private Long volume;

    @Schema(description = "?? ????", example = "3300000000000", nullable = true)
    private Long turnover;

    public RealtimeQuoteDto(String code, String time, double price, Double open, Double high, Double low, Double change, Double changeRate, Long volume, Long turnover) {
        this.code = code;
        this.time = time;
        this.price = price;
        this.open = open;
        this.high = high;
        this.low = low;
        this.change = change;
        this.changeRate = changeRate;
        this.volume = volume;
        this.turnover = turnover;
    }

    public String getCode() { return code; }
    public String getTime() { return time; }
    public double getPrice() { return price; }
    public Double getOpen() { return open; }
    public Double getHigh() { return high; }
    public Double getLow() { return low; }
    public Double getChange() { return change; }
    public Double getChangeRate() { return changeRate; }
    public Long getVolume() { return volume; }
    public Long getTurnover() { return turnover; }
}
