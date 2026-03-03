package com.example.backend.trading;

import com.example.backend.auth.JwtService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trading")
public class TradingController {
    private final TradingService tradingService;
    private final JwtService jwtService;

    public TradingController(TradingService tradingService, JwtService jwtService) {
        this.tradingService = tradingService;
        this.jwtService = jwtService;
    }

    @PostMapping("/account/link")
    public TradingAccountLinkDto linkAccount(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody TradingAccountLinkRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return tradingService.upsertAccountLink(userId, request);
    }

    @GetMapping("/account/link")
    public TradingAccountLinkDto getAccountLink(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return tradingService.getMyAccountLink(userId);
    }

    @GetMapping("/portfolio")
    public TradingPortfolioDto getPortfolio(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return tradingService.getPortfolio(userId);
    }

    @PostMapping("/orders")
    public TradingOrderResponseDto placeOrder(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody TradingOrderRequestDto request
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return tradingService.placeOrder(userId, request);
    }

    @GetMapping("/orders")
    public List<TradingOrderDto> getOrders(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return tradingService.getOrders(userId);
    }

    @GetMapping("/executions")
    public List<TradingExecutionDto> getExecutions(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return tradingService.getExecutions(userId);
    }
}
