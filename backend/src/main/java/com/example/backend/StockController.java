package com.example.backend;

import com.example.backend.ChartDataDto;
import com.example.backend.StockService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stock")
@CrossOrigin(origins = "http://localhost:3000") // CORS 에러 방지
public class StockController {
    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/chart/{code}")
    public List<ChartDataDto> getChart(@PathVariable String code) {
        return stockService.getDailyChart(code);
    }
}