package com.flowforge.worker;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkerHeartbeatResponse(
        String workerId,
        String hostname,
        String status,
        UUID currentJobId,
        LocalDateTime startedAt,
        LocalDateTime lastHeartbeatAt,
        Integer jobsStarted,
        Integer jobsSucceeded,
        Integer jobsFailed
) {
    public static WorkerHeartbeatResponse from(WorkerHeartbeatEntity entity) {
        return new WorkerHeartbeatResponse(
                entity.getWorkerId(),
                entity.getHostname(),
                entity.getStatus(),
                entity.getCurrentJobId(),
                entity.getStartedAt(),
                entity.getLastHeartbeatAt(),
                entity.getJobsStarted(),
                entity.getJobsSucceeded(),
                entity.getJobsFailed()
        );
    }
}