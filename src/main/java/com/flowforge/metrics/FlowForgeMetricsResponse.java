package com.flowforge.metrics;

public record FlowForgeMetricsResponse(
        long queuedJobs,
        long runningJobs,
        long scheduledJobs,
        long succeededJobs,
        long deadJobs,
        long totalWorkers,
        long idleWorkers,
        long runningWorkers,
        long redisStreamLength
) {}