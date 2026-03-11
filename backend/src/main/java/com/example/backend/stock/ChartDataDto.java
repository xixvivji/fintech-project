package com.example.backend.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "?? ?? ???")
public class ChartDataDto {
    @Schema(description = "?? ????(??: yyyy-MM-dd, ??: yyyy-MM-ddTHH:mm)", example = "2026-03-10")
    private String time;

    @Schema(description = "??", example = "187600.0")
    private double open;

    @Schema(description = "??", example = "191500.0")
    private double high;

    @Schema(description = "??", example = "184300.0")
    private double low;

    @Schema(description = "??", example = "186600.0")
    private double close;

    @Schema(description = "???", example = "27688959", nullable = true)
    private Long volume;

    @Schema(description = "????", example = "3300000000000", nullable = true)
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
