package com.flowforge.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.queue.JobQueuePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobQueuePublisher queuePublisher;
    private final ObjectMapper objectMapper;

    public JobService(
            JobRepository jobRepository,
            JobQueuePublisher queuePublisher,
            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.queuePublisher = queuePublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobEntity submitJob(SubmitJobRequest request) {
        JobEntity job = new JobEntity();

        job.setId(UUID.randomUUID());
        job.setType(request.type());
        job.setPayload(toJson(request.payload() == null ? Map.of() : request.payload()));
        job.setStatus("QUEUED");
        job.setPriority(request.priority() == null ? 0 : request.priority());
        job.setMaxAttempts(request.maxAttempts() == null ? 3 : request.maxAttempts());
        job.setNextRunAt(LocalDateTime.now());

        JobEntity savedJob = jobRepository.save(job);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    queuePublisher.publish(savedJob.getId());
                }
            });
        } else {
            queuePublisher.publish(savedJob.getId());
        }

        return savedJob;
    }

    public JobEntity getJob(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    public List<JobEntity> listJobs(String status) {
        if (status == null || status.isBlank()) {
            return jobRepository.findAll();
        }

        return jobRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }
}