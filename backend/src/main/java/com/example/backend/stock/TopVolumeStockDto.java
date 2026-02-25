package com.example.backend.stock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopVolumeStockDto {
    private String code;
    private String tradeDate;
    private double closePrice;
    private Long volume;
}
