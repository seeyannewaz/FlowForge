package com.flowforge.worker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class WorkerHeartbeatService {

    private final WorkerHeartbeatRepository repository;

    public WorkerHeartbeatService(WorkerHeartbeatRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registerWorker(String workerId, String hostname) {
        WorkerHeartbeatEntity heartbeat = repository.findById(workerId)
                .orElseGet(WorkerHeartbeatEntity::new);

        heartbeat.setWorkerId(workerId);
        heartbeat.setHostname(hostname);
        heartbeat.setStatus("IDLE");
        heartbeat.setCurrentJobId(null);
        heartbeat.setLastHeartbeatAt(LocalDateTime.now());

        if (heartbeat.getStartedAt() == null) {
            heartbeat.setStartedAt(LocalDateTime.now());
        }

        repository.save(heartbeat);

        System.out.println("Registered worker heartbeat: " + workerId);
    }

    @Transactional
    public void heartbeat(String workerId) {
        repository.findById(workerId).ifPresent(heartbeat -> {
            heartbeat.setLastHeartbeatAt(LocalDateTime.now());
            repository.save(heartbeat);
        });
    }

    @Transactional
    public void markRunning(String workerId, UUID jobId) {
        repository.findById(workerId).ifPresent(heartbeat -> {
            heartbeat.setStatus("RUNNING");
            heartbeat.setCurrentJobId(jobId);
            heartbeat.setLastHeartbeatAt(LocalDateTime.now());
            heartbeat.setJobsStarted(heartbeat.getJobsStarted() + 1);
            repository.save(heartbeat);
        });
    }

    @Transactional
    public void markJobSucceeded(String workerId) {
        repository.findById(workerId).ifPresent(heartbeat -> {
            heartbeat.setStatus("IDLE");
            heartbeat.setCurrentJobId(null);
            heartbeat.setLastHeartbeatAt(LocalDateTime.now());
            heartbeat.setJobsSucceeded(heartbeat.getJobsSucceeded() + 1);
            repository.save(heartbeat);
        });
    }

    @Transactional
    public void markJobFailed(String workerId) {
        repository.findById(workerId).ifPresent(heartbeat -> {
            heartbeat.setStatus("IDLE");
            heartbeat.setCurrentJobId(null);
            heartbeat.setLastHeartbeatAt(LocalDateTime.now());
            heartbeat.setJobsFailed(heartbeat.getJobsFailed() + 1);
            repository.save(heartbeat);
        });
    }

    @Transactional
    public void markIdle(String workerId) {
        repository.findById(workerId).ifPresent(heartbeat -> {
            heartbeat.setStatus("IDLE");
            heartbeat.setCurrentJobId(null);
            heartbeat.setLastHeartbeatAt(LocalDateTime.now());
            repository.save(heartbeat);
        });
    }

    @Transactional
    public void markStopped(String workerId) {
        repository.findById(workerId).ifPresent(heartbeat -> {
            heartbeat.setStatus("STOPPED");
            heartbeat.setCurrentJobId(null);
            heartbeat.setLastHeartbeatAt(LocalDateTime.now());
            repository.save(heartbeat);
        });
    }
}