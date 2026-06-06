package com.flowforge.job;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String type,
        String payload,
        String result,
        String status,
        Integer priority,
        Integer attemptCount,
        Integer maxAttempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {
    public static JobResponse from(JobEntity job) {
        return new JobResponse(
                job.getId(),
                job.getType(),
                job.getPayload(),
                job.getResult(),
                job.getStatus(),
                job.getPriority(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getLastError(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt()
        );
    }
}