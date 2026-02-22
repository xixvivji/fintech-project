package com.example.backend;

import com.example.backend.ChartDataDto;
import com.example.backend.StockService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {
    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/chart/{code}")
    public List<ChartDataDto> getChart(
            @PathVariable String code,
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) String endDate
    ) {
        return stockService.getDailyChart(code, months, endDate);
    }
}
