package com.example.backend.simulation.time;

import org.springframework.stereotype.Component;

@Component
public class TimeEngineProvider {
    private final SimTimeProperties properties;
    private final ReplayTimeEngine replayTimeEngine;
    private final LiveTimeEngine liveTimeEngine;

    public TimeEngineProvider(SimTimeProperties properties, ReplayTimeEngine replayTimeEngine, LiveTimeEngine liveTimeEngine) {
        this.properties = properties;
        this.replayTimeEngine = replayTimeEngine;
        this.liveTimeEngine = liveTimeEngine;
    }

    public TimeEngine get() {
        String mode = properties.getMode();
        if (mode != null && "LIVE".equalsIgnoreCase(mode.trim())) {
            return liveTimeEngine;
        }
        return replayTimeEngine;
    }
}
