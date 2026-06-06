package com.flowforge.handlers;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FailJobHandler implements JobHandler {

    @Override
    public String type() {
        return "fail";
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> payload) {
        throw new RuntimeException("Intentional failure for testing retry/dead-letter behavior");
    }
}