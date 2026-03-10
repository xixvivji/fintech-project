package com.example.backend.stock;

public class RealtimeQuoteDto {
    private String code;
    private String time;
    private double price;
    private Double open;
    private Double high;
    private Double low;
    private Double change;
    private Double changeRate;
    private Long volume;
    private Long turnover;

    public RealtimeQuoteDto(
            String code,
            String time,
            double price,
            Double open,
            Double high,
            Double low,
            Double change,
            Double changeRate,
            Long volume,
            Long turnover
    ) {
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

    public String getCode() {
        return code;
    }

    public String getTime() {
        return time;
    }

    public double getPrice() {
        return price;
    }

    public Double getOpen() {
        return open;
    }

    public Double getHigh() {
        return high;
    }

    public Double getLow() {
        return low;
    }

    public Double getChange() {
        return change;
    }

    public Double getChangeRate() {
        return changeRate;
    }

    public Long getVolume() {
        return volume;
    }

    public Long getTurnover() {
        return turnover;
    }
}
