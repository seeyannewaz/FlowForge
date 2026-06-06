package com.flowforge.handlers;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class SleepJobHandler implements JobHandler {

    @Override
    public String type() {
        return "sleep";
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> payload) throws InterruptedException {
        int milliseconds = ((Number) payload.getOrDefault("milliseconds", 1000)).intValue();

        if (milliseconds > 10000) {
            throw new IllegalArgumentException("Sleep duration cannot exceed 10000 ms");
        }

        Thread.sleep(milliseconds);

        return Map.of(
                "sleptForMs", milliseconds,
                "processedAt", LocalDateTime.now().toString()
        );
    }
}