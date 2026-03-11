package com.example.backend.stock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@Tag(name = "Stock", description = "??/??/??/?? API")
public class StockController {
    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @Operation(
            summary = "?? ?? ??",
            description = "??? ?? OHLCV(? ????)? ?????. endDate? ??? ?? ???? ?????."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "?? ??",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChartDataDto.class)))),
            @ApiResponse(responseCode = "400", description = "???? ??")
    })
    @GetMapping("/chart/{code}")
    public List<ChartDataDto> getChart(
            @Parameter(description = "6?? ????", example = "005930") @PathVariable String code,
            @Parameter(description = "?? ? ?(1~120)", example = "6") @RequestParam(defaultValue = "6") int months,
            @Parameter(description = "?? ???(yyyy-MM-dd)", example = "2026-03-10") @RequestParam(required = false) String endDate
    ) {
        return stockService.getDailyChart(code, months, endDate);
    }

    @Operation(
            summary = "?? ?? ??",
            description = "??? 1? ?? ???? N? ?? ?????. days/endDate? ??? ?? ??? ??? ? ????."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "?? ??",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChartDataDto.class)))),
            @ApiResponse(responseCode = "400", description = "???? ??")
    })
    @GetMapping("/chart/intraday/{code}")
    public List<ChartDataDto> getIntradayChart(
            @Parameter(description = "6?? ????", example = "005930") @PathVariable String code,
            @Parameter(description = "?? ??(1, 10, 30, 60)", example = "1") @RequestParam(defaultValue = "1") int minutes,
            @Parameter(description = "?? ?? ?", example = "240") @RequestParam(defaultValue = "240") int limit,
            @Parameter(description = "?? ???(yyyy-MM-dd)", example = "2026-03-10") @RequestParam(required = false) String endDate,
            @Parameter(description = "?? ??(1~60)", example = "1") @RequestParam(defaultValue = "1") int days
    ) {
        return stockService.getIntradayChart(code, minutes, limit, endDate, days);
    }

    @Operation(summary = "??? ?? ??", description = "??? ?? ??? ?? ?? ???? ?????.")
    @GetMapping("/top-volume")
    public List<TopVolumeStockDto> getTopVolume(
            @Parameter(description = "???(yyyy-MM-dd), ??? ? ?? ???", example = "2026-03-10") @RequestParam(required = false) String date,
            @Parameter(description = "?? ??", example = "10") @RequestParam(defaultValue = "10") int limit
    ) {
        return stockService.getTopVolumeStocks(date, limit);
    }

    @Operation(summary = "??? ?/??", description = "??/?? TOP ??? ?????.")
    @GetMapping("/top-movers")
    public TopMoversResponseDto getTopMovers(
            @Parameter(description = "???(yyyy-MM-dd), ??? ? ?? ???", example = "2026-03-10") @RequestParam(required = false) String date,
            @Parameter(description = "?/?? ??", example = "5") @RequestParam(defaultValue = "5") int limit
    ) {
        return stockService.getTopMovers(date, limit);
    }

    @Operation(summary = "??? ??", description = "???/???/???/????/???? ?????.")
    @GetMapping("/quote/{code}")
    public RealtimeQuoteDto getRealtimeQuote(
            @Parameter(description = "6?? ????", example = "005930") @PathVariable String code
    ) {
        return stockService.getRealtimeQuote(code);
    }

    @Operation(summary = "??? ??/????", description = "?? ??, ???, ???? ??? ?????.")
    @GetMapping("/orderbook/{code}")
    public OrderBookDto getOrderBook(
            @Parameter(description = "6?? ????", example = "005930") @PathVariable String code
    ) {
        return stockService.getOrderBook(code);
    }

    @Operation(summary = "?? ??", description = "?? ??? ?? ?? ???? ??? DB? ?????.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "?? ??",
                    content = @Content(schema = @Schema(implementation = IntradayBackfillResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "?? ?? ??")
    })
    @PostMapping("/backfill/intraday")
    public IntradayBackfillResponseDto backfillIntraday(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "?? ?? ?? ??",
                    content = @Content(schema = @Schema(implementation = IntradayBackfillRequestDto.class))
            )
            @RequestBody IntradayBackfillRequestDto request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }
        return stockService.backfillIntradayCandles(request.getCodes(), request.getLimit(), request.getDays(), request.getEndDate());
    }

    @Operation(summary = "?? ??", description = "?? ??? ?? ?? ???? ??? DB? ?????.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "?? ??",
                    content = @Content(schema = @Schema(implementation = StockBackfillResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "?? ?? ??")
    })
    @PostMapping("/backfill")
    public StockBackfillResponseDto backfill(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "?? ?? ?? ??",
                    content = @Content(schema = @Schema(implementation = StockBackfillRequestDto.class))
            )
            @RequestBody StockBackfillRequestDto request
    ) {
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
