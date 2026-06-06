package com.flowforge.worker;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "worker_heartbeats")
public class WorkerHeartbeatEntity {

    @Id
    @Column(length = 150)
    private String workerId;

    @Column(nullable = false)
    private String hostname;

    @Column(nullable = false)
    private String status;

    private UUID currentJobId;

    @Column(nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime lastHeartbeatAt = LocalDateTime.now();

    @Column(nullable = false)
    private Integer jobsStarted = 0;

    @Column(nullable = false)
    private Integer jobsSucceeded = 0;

    @Column(nullable = false)
    private Integer jobsFailed = 0;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getCurrentJobId() {
        return currentJobId;
    }

    public void setCurrentJobId(UUID currentJobId) {
        this.currentJobId = currentJobId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Integer getJobsStarted() {
        return jobsStarted;
    }

    public void setJobsStarted(Integer jobsStarted) {
        this.jobsStarted = jobsStarted;
    }

    public Integer getJobsSucceeded() {
        return jobsSucceeded;
    }

    public void setJobsSucceeded(Integer jobsSucceeded) {
        this.jobsSucceeded = jobsSucceeded;
    }

    public Integer getJobsFailed() {
        return jobsFailed;
    }

    public void setJobsFailed(Integer jobsFailed) {
        this.jobsFailed = jobsFailed;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}