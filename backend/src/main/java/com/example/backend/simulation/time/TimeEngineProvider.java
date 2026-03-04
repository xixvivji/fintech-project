package com.example.backend.simulation.time;

import org.springframework.stereotype.Component;

@Component
public class TimeEngineProvider {
    private final LiveTimeEngine liveTimeEngine;

    public TimeEngineProvider(SimTimeProperties properties, ReplayTimeEngine replayTimeEngine, LiveTimeEngine liveTimeEngine) {
        this.liveTimeEngine = liveTimeEngine;
    }

    public TimeEngine get() {
        return liveTimeEngine;
    }
}
