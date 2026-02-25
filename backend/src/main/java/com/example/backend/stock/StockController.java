package com.example.backend.stock;

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

    @GetMapping("/top-volume")
    public List<TopVolumeStockDto> getTopVolume(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return stockService.getTopVolumeStocks(date, limit);
    }

    @GetMapping("/top-movers")
    public TopMoversResponseDto getTopMovers(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return stockService.getTopMovers(date, limit);
    }

    @PostMapping("/backfill")
    public StockBackfillResponseDto backfill(@RequestBody StockBackfillRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        return stockService.backfillDailyPrices(
                request.getCodes(),
                request.getStartDate(),
                request.getEndDate(),
                request.getChunkMonths()
        );
    }
}
