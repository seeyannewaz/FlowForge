package com.flowforge.handlers;

import java.util.Map;

public interface JobHandler {
    String type();

    Map<String, Object> handle(Map<String, Object> payload) throws Exception;
}