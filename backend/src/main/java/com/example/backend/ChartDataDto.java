package com.example.backend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChartDataDto {
    private String time;  // 포맷: "yyyy-MM-dd"
    private double open;
    private double high;
    private double low;
    private double close;
}