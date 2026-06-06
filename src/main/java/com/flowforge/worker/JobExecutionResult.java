package com.flowforge.worker;

import java.util.UUID;

public record JobExecutionResult(
        UUID jobId,
        boolean processed,
        String finalStatus,
        String errorMessage
) {}