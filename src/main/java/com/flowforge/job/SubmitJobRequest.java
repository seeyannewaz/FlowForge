package com.flowforge.job;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record SubmitJobRequest(
        @NotBlank String type,
        Map<String, Object> payload,
        Integer priority,
        Integer maxAttempts
) {}