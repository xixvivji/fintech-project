package com.example.backend.simulation;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sim")
public class SimulationController {
    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/portfolio")
    public PortfolioResponseDto getPortfolio() {
        return simulationService.getPortfolio();
    }

    @PostMapping("/order")
    public SimOrderResponseDto placeOrder(@RequestBody SimOrderRequestDto request) {
        return simulationService.placeMarketOrder(request);
    }

    @PostMapping("/reset")
    public PortfolioResponseDto reset() {
        simulationService.reset();
        return simulationService.getPortfolio();
    }

    @PostMapping("/replay/start")
    public PortfolioResponseDto startReplay(@RequestBody(required = false) ReplayStartRequestDto request) {
        String startDate = request == null ? null : request.getStartDate();
        return simulationService.startReplay(startDate);
    }

    @PostMapping("/replay/pause")
    public PortfolioResponseDto pauseReplay() {
        return simulationService.pauseReplay();
    }

    @GetMapping("/replay/state")
    public ReplayStateDto getReplayState() {
        return simulationService.getReplayState();
    }
}
