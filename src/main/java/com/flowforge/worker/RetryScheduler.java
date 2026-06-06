package com.flowforge.worker;

import com.flowforge.job.JobEntity;
import com.flowforge.job.JobRepository;
import com.flowforge.queue.JobQueuePublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class RetryScheduler {

    private final JobRepository jobRepository;
    private final JobQueuePublisher queuePublisher;

    public RetryScheduler(
            JobRepository jobRepository,
            JobQueuePublisher queuePublisher
    ) {
        this.jobRepository = jobRepository;
        this.queuePublisher = queuePublisher;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void requeueReadyJobs() {
        List<JobEntity> readyJobs =
                jobRepository.findByStatusAndNextRunAtLessThanEqualOrderByPriorityDescCreatedAtAsc(
                        "SCHEDULED",
                        LocalDateTime.now()
                );

        if (readyJobs.isEmpty()) {
            return;
        }

        List<UUID> jobIdsToPublish = new ArrayList<>();

        for (JobEntity job : readyJobs) {
            job.setStatus("QUEUED");
            jobIdsToPublish.add(job.getId());
        }

        jobRepository.saveAll(readyJobs);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (UUID jobId : jobIdsToPublish) {
                    queuePublisher.publish(jobId);
                    System.out.println("Requeued scheduled job: " + jobId);
                }
            }
        });
    }
}