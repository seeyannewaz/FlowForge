package com.flowforge.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.handlers.JobHandler;
import com.flowforge.job.JobAttemptEntity;
import com.flowforge.job.JobAttemptRepository;
import com.flowforge.job.JobEntity;
import com.flowforge.job.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JobExecutionService {

    private final JobRepository jobRepository;
    private final JobAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, JobHandler> handlersByType;

    public JobExecutionService(
            JobRepository jobRepository,
            JobAttemptRepository attemptRepository,
            ObjectMapper objectMapper,
            List<JobHandler> handlers) {
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.handlersByType = handlers.stream()
                .collect(Collectors.toMap(JobHandler::type, handler -> handler));
    }

    private int calculateBackoffSeconds(int attemptNumber) {
        int delay = (int) Math.pow(2, attemptNumber);
        return Math.min(delay, 60);
    }

    @Transactional
    public void execute(UUID jobId, String workerId) {
        JobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (!job.getStatus().equals("QUEUED")) {
            return;
        }

        int attemptNumber = job.getAttemptCount() + 1;

        JobAttemptEntity attempt = new JobAttemptEntity();
        attempt.setJobId(job.getId());
        attempt.setAttemptNumber(attemptNumber);
        attempt.setWorkerId(workerId);
        attempt.setStatus("RUNNING");
        attemptRepository.save(attempt);

        job.setStatus("RUNNING");
        job.setAttemptCount(attemptNumber);
        job.setLockedBy(workerId);
        job.setLockedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            JobHandler handler = handlersByType.get(job.getType());

            if (handler == null) {
                throw new IllegalArgumentException("No handler registered for job type: " + job.getType());
            }

            Map<String, Object> payload = objectMapper.readValue(
                    job.getPayload(),
                    new TypeReference<>() {
                    });

            Map<String, Object> result = handler.handle(payload);

            job.setStatus("SUCCEEDED");
            job.setResult(objectMapper.writeValueAsString(result));
            job.setCompletedAt(LocalDateTime.now());
            job.setLastError(null);

            attempt.setStatus("SUCCEEDED");
            attempt.setFinishedAt(LocalDateTime.now());

        } catch (Exception e) {
            job.setLastError(e.getMessage());

            if (attemptNumber >= job.getMaxAttempts()) {
                job.setStatus("DEAD");
                job.setCompletedAt(LocalDateTime.now());
            } else {
                int delaySeconds = calculateBackoffSeconds(attemptNumber);

                job.setStatus("SCHEDULED");
                job.setNextRunAt(LocalDateTime.now().plusSeconds(delaySeconds));

                System.out.println(
                        "Job " + job.getId() +
                                " failed on attempt " + attemptNumber +
                                ". Retrying in " + delaySeconds + " seconds.");
            }

            attempt.setStatus("FAILED");
            attempt.setError(e.getMessage());
            attempt.setFinishedAt(LocalDateTime.now());
        }

        jobRepository.save(job);
        attemptRepository.save(attempt);
    }
}