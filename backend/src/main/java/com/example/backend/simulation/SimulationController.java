package com.example.backend.simulation;

import com.example.backend.auth.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/sim")
@Tag(name = "Simulation", description = "????/????/??/???? API")
public class SimulationController {
    private static final long POPULAR_STOCKS_SSE_INTERVAL_MS = 15_000L;

    private final SimulationService simulationService;
    private final JwtService jwtService;
    private final ScheduledExecutorService sseExecutor = Executors.newScheduledThreadPool(2);

    public SimulationController(SimulationService simulationService, JwtService jwtService) {
        this.simulationService = simulationService;
        this.jwtService = jwtService;
    }

    @PreDestroy
    public void shutdownSseExecutor() {
        sseExecutor.shutdownNow();
    }

    @Operation(summary = "? ????? ??", description = "?? ??? ?????/????/?? ??? ?????.")
    @GetMapping("/portfolio")
    public PortfolioResponseDto getPortfolio(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getPortfolio(userId);
    }

    @Operation(summary = "?? ??", description = "???/??? ????? ??? ?????.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "?? ?? ??",
                    content = @Content(schema = @Schema(implementation = SimOrderResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "?? ???? ??")
    })
    @PostMapping("/order")
    public SimOrderResponseDto placeOrder(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "?? ?? ??",
                    content = @Content(schema = @Schema(implementation = SimOrderRequestDto.class))
            )
            @RequestBody SimOrderRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.placeOrder(userId, request);
    }

    @Operation(summary = "???? ???", description = "???? ?? ?????? ?? ??? ?????.")
    @PostMapping("/reset")
    public PortfolioResponseDto reset(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        simulationService.reset(userId);
        return simulationService.getPortfolio(userId);
    }

    @Operation(summary = "???? ??", description = "???(startDate)? ????? ?????.")
    @PostMapping("/replay/start")
    public PortfolioResponseDto startReplay(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = false,
                    description = "???? ?? ?? ??",
                    content = @Content(schema = @Schema(implementation = ReplayStartRequestDto.class))
            )
            @RequestBody(required = false) ReplayStartRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        String startDate = request == null ? null : request.getStartDate();
        return simulationService.startReplay(userId, startDate);
    }

    @Operation(summary = "???? ????")
    @PostMapping("/replay/pause")
    public PortfolioResponseDto pauseReplay(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.pauseReplay(userId);
    }

    @Operation(summary = "???? ?? ??")
    @GetMapping("/replay/state")
    public ReplayStateDto getReplayState(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getReplayState(userId);
    }

    @Operation(summary = "?? ?? ??")
    @GetMapping("/league-state")
    public SimLeagueStateDto getLeagueState(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getLeagueState(userId);
    }

    @Operation(summary = "??? ?? ??")
    @ApiResponse(responseCode = "200", description = "?? ??",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PendingOrderDto.class))))
    @GetMapping("/orders/pending")
    public List<PendingOrderDto> getPendingOrders(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getPendingOrders(userId);
    }

    @Operation(summary = "?? ?? ??")
    @ApiResponse(responseCode = "200", description = "?? ??",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TradeExecutionDto.class))))
    @GetMapping("/orders/executions")
    public List<TradeExecutionDto> getTradeExecutions(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getTradeExecutions(userId);
    }

    @Operation(summary = "?? ? ??")
    @GetMapping("/orders/queue-status")
    public SimOrderQueueStatusDto getOrderQueueStatus(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getOrderQueueStatus(userId);
    }

    @Operation(summary = "?? ?? ??")
    @ApiResponse(responseCode = "200", description = "?? ??",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SimRankingDto.class))))
    @GetMapping("/rankings")
    public List<SimRankingDto> getRankings(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getRankings(userId);
    }

    @Operation(summary = "?? ?? ??", description = "??/??? ?? ?? ??? ?????.")
    @ApiResponse(responseCode = "200", description = "?? ??",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SimPopularStockDto.class))))
    @GetMapping("/popular-stocks")
    public List<SimPopularStockDto> getPopularStocks(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "?? ??", example = "10") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "?? ??(?)", example = "7") @RequestParam(defaultValue = "7") int days
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getPopularStocks(userId, limit, days);
    }

    @Operation(summary = "?? ?? SSE ???", description = "accessToken ???? ?? ??? 15??? SSE? ?????.")
    @GetMapping(value = "/popular-stocks/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPopularStocks(
            @Parameter(description = "Bearer ??? ?? ??? ??", required = true) @RequestParam String accessToken,
            @Parameter(description = "?? ??", example = "10") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "?? ??(?)", example = "7") @RequestParam(defaultValue = "7") int days
    ) {
        Long userId = jwtService.validateAndGetUserIdFromToken(accessToken);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean closed = new AtomicBoolean(false);

        Runnable publish = () -> {
            if (closed.get()) return;
            try {
                List<SimPopularStockDto> rows = simulationService.getPopularStocks(userId, limit, days);
                emitter.send(SseEmitter.event().name("popular-stocks").data(rows));
            } catch (Exception e) {
                closed.set(true);
                emitter.completeWithError(e);
            }
        };

        publish.run();
        ScheduledFuture<?> future = sseExecutor.scheduleAtFixedRate(
                publish,
                POPULAR_STOCKS_SSE_INTERVAL_MS,
                POPULAR_STOCKS_SSE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        Runnable cleanup = () -> {
            closed.set(true);
            future.cancel(true);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(err -> cleanup.run());
        return emitter;
    }

    @Operation(summary = "?? ?? ????? ??")
    @GetMapping("/rankings/{targetUserId}/portfolio")
    public SimRankingPortfolioSummaryDto getRankingPortfolioSummary(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "?? ?? ??? ID", example = "12") @PathVariable Long targetUserId
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getRankingPortfolioSummary(userId, targetUserId);
    }

    @Operation(summary = "??? ?? ??")
    @DeleteMapping("/orders/pending/{orderId}")
    public ResponseEntity<Void> cancelPendingOrder(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "??? ?? ID", example = "1024") @PathVariable Long orderId
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        simulationService.cancelPendingOrder(userId, orderId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "???? ?? ??")
    @ApiResponse(responseCode = "200", description = "?? ??",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = SimAutoBuyRuleDto.class))))
    @GetMapping("/auto-buy-rules")
    public List<SimAutoBuyRuleDto> getAutoBuyRules(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getAutoBuyRules(userId);
    }

    @Operation(summary = "???? ?? ??")
    @PostMapping("/auto-buy-rules")
    public ResponseEntity<SimAutoBuyRuleDto> createAutoBuyRule(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "???? ?? ?? ??",
                    content = @Content(schema = @Schema(implementation = SimAutoBuyRuleRequestDto.class))
            )
            @RequestBody SimAutoBuyRuleRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return ResponseEntity.status(201).body(simulationService.createAutoBuyRule(userId, request));
    }

    @Operation(summary = "???? ?? ??")
    @PatchMapping("/auto-buy-rules/{ruleId}")
    public SimAutoBuyRuleDto updateAutoBuyRule(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "?? ID", example = "7") @PathVariable Long ruleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "???? ?? ?? ??",
                    content = @Content(schema = @Schema(implementation = SimAutoBuyRuleRequestDto.class))
            )
            @RequestBody SimAutoBuyRuleRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.updateAutoBuyRule(userId, ruleId, request);
    }

    @Operation(summary = "???? ?? ??")
    @DeleteMapping("/auto-buy-rules/{ruleId}")
    public ResponseEntity<Void> deleteAutoBuyRule(
            @Parameter(description = "Bearer ??? ??", required = true, example = "Bearer eyJ...")
            @RequestHeader("Authorization") String authorizationHeader,
            @Parameter(description = "?? ID", example = "7") @PathVariable Long ruleId
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        simulationService.deleteAutoBuyRule(userId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
