package com.flowforge.handlers;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class EchoJobHandler implements JobHandler {

    @Override
    public String type() {
        return "echo";
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", payload.getOrDefault("message", "No message provided"));
        result.put("processedAt", LocalDateTime.now().toString());
        return result;
    }
}