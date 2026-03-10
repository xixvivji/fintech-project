package com.example.backend.stock;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChartDataDto {
    private String time;
    private double open;
    private double high;
    private double low;
    private double close;
    private Long volume;
    private Long turnover;

    public ChartDataDto(String time, double open, double high, double low, double close) {
        this(time, open, high, low, close, null, null);
    }

    public ChartDataDto(String time, double open, double high, double low, double close, Long volume) {
        this(time, open, high, low, close, volume, null);
    }

    public ChartDataDto(String time, double open, double high, double low, double close, Long volume, Long turnover) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.turnover = turnover;
    }
}
