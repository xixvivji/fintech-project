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

    public ChartDataDto(String time, double open, double high, double low, double close) {
        this(time, open, high, low, close, null);
    }

    public ChartDataDto(String time, double open, double high, double low, double close, Long volume) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
}
