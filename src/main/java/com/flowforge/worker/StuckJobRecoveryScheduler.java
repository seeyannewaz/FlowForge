package com.flowforge.worker;

import com.flowforge.job.JobEntity;
import com.flowforge.job.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class StuckJobRecoveryScheduler {

    private final JobRepository jobRepository;
    private final WorkerHeartbeatRepository heartbeatRepository;
    private final long runningTimeoutSeconds;
    private final long heartbeatTimeoutSeconds;

    public StuckJobRecoveryScheduler(
            JobRepository jobRepository,
            WorkerHeartbeatRepository heartbeatRepository,
            @Value("${flowforge.recovery.running-timeout-seconds}") long runningTimeoutSeconds,
            @Value("${flowforge.recovery.heartbeat-timeout-seconds}") long heartbeatTimeoutSeconds) {
        this.jobRepository = jobRepository;
        this.heartbeatRepository = heartbeatRepository;
        this.runningTimeoutSeconds = runningTimeoutSeconds;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${flowforge.recovery.scan-ms}")
    @Transactional
    public void recoverStuckJobs() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime runningCutoff = now.minusSeconds(runningTimeoutSeconds);
        LocalDateTime heartbeatCutoff = now.minusSeconds(heartbeatTimeoutSeconds);

        List<JobEntity> suspiciousJobs = jobRepository.findRecoverableRunningJobs(runningCutoff);
        System.out.println(
                "[Recovery] scan runningCutoff=" + runningCutoff +
                        ", suspiciousJobs=" + suspiciousJobs.size());

        if (suspiciousJobs.isEmpty()) {
            return;
        }

        for (JobEntity job : suspiciousJobs) {
            String workerId = job.getLockedBy();

            if (workerId != null && isWorkerStillAlive(workerId, heartbeatCutoff)) {
                continue;
            }

            recoverJob(job, now);
        }
    }

    private boolean isWorkerStillAlive(String workerId, LocalDateTime heartbeatCutoff) {
        return heartbeatRepository.findById(workerId)
                .map(worker -> worker.getLastHeartbeatAt().isAfter(heartbeatCutoff))
                .orElse(false);
    }

    private void recoverJob(JobEntity job, LocalDateTime now) {
        if (job.getCompletedAt() != null) {
            System.out.println("Skipping already completed job during recovery: " + job.getId());
            return;
        }
        String message = "Recovered stuck RUNNING job. Worker " +
                job.getLockedBy() +
                " missed heartbeat timeout.";

        job.setLastError(message);
        job.setLockedBy(null);
        job.setLockedAt(null);

        if (job.getAttemptCount() >= job.getMaxAttempts()) {
            job.setStatus("DEAD");
            job.setCompletedAt(now);

            System.out.println("Recovered stuck job as DEAD: " + job.getId());
        } else {
            int delaySeconds = calculateBackoffSeconds(job.getAttemptCount());

            job.setStatus("SCHEDULED");
            job.setNextRunAt(now.plusSeconds(delaySeconds));

            System.out.println(
                    "Recovered stuck job " + job.getId() +
                            " and scheduled retry in " + delaySeconds + " seconds.");
        }

        jobRepository.save(job);
    }

    private int calculateBackoffSeconds(int attemptNumber) {
        int delay = (int) Math.pow(2, Math.max(attemptNumber, 1));
        return Math.min(delay, 60);
    }
}