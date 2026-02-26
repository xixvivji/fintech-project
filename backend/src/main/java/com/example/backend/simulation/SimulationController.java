package com.example.backend.simulation;

import com.example.backend.auth.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sim")
public class SimulationController {
    private final SimulationService simulationService;
    private final JwtService jwtService;

    public SimulationController(SimulationService simulationService, JwtService jwtService) {
        this.simulationService = simulationService;
        this.jwtService = jwtService;
    }

    @GetMapping("/portfolio")
    public PortfolioResponseDto getPortfolio(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getPortfolio(userId);
    }

    @PostMapping("/order")
    public SimOrderResponseDto placeOrder(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody SimOrderRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.placeOrder(userId, request);
    }

    @PostMapping("/reset")
    public PortfolioResponseDto reset(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        simulationService.reset(userId);
        return simulationService.getPortfolio(userId);
    }

    @PostMapping("/replay/start")
    public PortfolioResponseDto startReplay(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody(required = false) ReplayStartRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        String startDate = request == null ? null : request.getStartDate();
        return simulationService.startReplay(userId, startDate);
    }

    @PostMapping("/replay/pause")
    public PortfolioResponseDto pauseReplay(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.pauseReplay(userId);
    }

    @GetMapping("/replay/state")
    public ReplayStateDto getReplayState(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getReplayState(userId);
    }

    @GetMapping("/league-state")
    public SimLeagueStateDto getLeagueState(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getLeagueState(userId);
    }

    @GetMapping("/orders/pending")
    public List<PendingOrderDto> getPendingOrders(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getPendingOrders(userId);
    }

    @GetMapping("/orders/executions")
    public List<TradeExecutionDto> getTradeExecutions(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getTradeExecutions(userId);
    }

    @GetMapping("/rankings")
    public List<SimRankingDto> getRankings(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getRankings(userId);
    }

    @GetMapping("/popular-stocks")
    public List<SimPopularStockDto> getPopularStocks(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "7") int days
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getPopularStocks(userId, limit, days);
    }

    @GetMapping("/rankings/{targetUserId}/portfolio")
    public SimRankingPortfolioSummaryDto getRankingPortfolioSummary(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long targetUserId
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getRankingPortfolioSummary(userId, targetUserId);
    }

    @DeleteMapping("/orders/pending/{orderId}")
    public ResponseEntity<Void> cancelPendingOrder(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long orderId
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        simulationService.cancelPendingOrder(userId, orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auto-buy-rules")
    public List<SimAutoBuyRuleDto> getAutoBuyRules(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.getAutoBuyRules(userId);
    }

    @PostMapping("/auto-buy-rules")
    public ResponseEntity<SimAutoBuyRuleDto> createAutoBuyRule(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody SimAutoBuyRuleRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return ResponseEntity.status(201).body(simulationService.createAutoBuyRule(userId, request));
    }

    @PatchMapping("/auto-buy-rules/{ruleId}")
    public SimAutoBuyRuleDto updateAutoBuyRule(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long ruleId,
            @RequestBody SimAutoBuyRuleRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return simulationService.updateAutoBuyRule(userId, ruleId, request);
    }

    @DeleteMapping("/auto-buy-rules/{ruleId}")
    public ResponseEntity<Void> deleteAutoBuyRule(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long ruleId
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        simulationService.deleteAutoBuyRule(userId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
